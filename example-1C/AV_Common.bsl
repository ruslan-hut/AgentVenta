///////////////////////////////////////////////////////////////////////////////
//
//  AV_Common — reference integration module: 1C  <->  AgentVenta mobile app
//               through the Sphynx relay server.
//
//  AgentVenta (Android app):  https://github.com/ruslan-hut/AgentVenta
//  Integration docs:          see the app repository, folder /docs
//                             (API_DEVICE_MANAGEMENT.md, API_DATA_MANAGEMENT.md)
//
//  WHAT THIS IS
//  A self-contained BSL common module that connects a 1C:Enterprise 8.3
//  configuration (here: "Управление небольшой фирмой" / Small Business
//  Management for Ukraine) to the AgentVenta app. 1C is a pure REST client of
//  the relay — it never exposes an HTTP service of its own. Connection settings
//  (relay URL, license token, registered devices) live in the catalog
//  AV_Settings; the scheduled job AV_ExchangeJob drives the exchange; changes
//  for differential upload are collected via the exchange plan AV_Exchange.
//
//  RELAY
//  Base URL is taken from AV_Settings.Server (default https://lic.nomadus.net).
//  Every request carries:  Authorization: Bearer {license_api_key}
//
//  1C -> device  (catalog push; each record tagged by its "value_id"):
//     options, item, price, image, client, debt, discount, ...
//     POST /api/v1/push            — one page of catalog records
//     POST /api/v1/push/complete   — end-of-batch marker (device prunes stale data)
//     PUT  /api/v1/images/{guid}   — upload image bytes (see UploadImage)
//
//  device -> 1C  (documents uploaded by agents, pulled and posted into 1C):
//     GET  /api/v1/pull            — a batch of documents (data_type: order, cash)
//     POST /api/v1/pull/ack        — confirm processed documents (at-least-once)
//
//  device management:
//     GET /api/v1/license, GET /api/v1/devices, POST /api/v1/devices/register,
//     DELETE /api/v1/devices/{uuid}
//
//  FOR INTEGRATORS
//  - The transport layer (JSON_*, HTTP_*, DoRequest / GET / DELETE, 429 retries,
//    paging) is generic; adapt the object-specific parts (Get*Data, Download*)
//    to your own Documents / Catalogs / Registers.
//  - Idempotency: uploaded documents are keyed by their app "guid"; re-delivery
//    (e.g. after a pull/ack that never reached the server) is safe — an already
//    posted document is skipped.
//  - Only objects prefixed AV_ belong to the integration; the base vendor
//    configuration is left untouched.
//
//  This module is a reference implementation, not drop-in code.
//
///////////////////////////////////////////////////////////////////////////////

#Region JSON

Function JSON_Marshall(Obj, Err=Undefined, Pretty=False)

	S = "";

	Try
		Writer = New JSONWriter;
		If Pretty Then
			Settings = New JSONWriterSettings(JSONLineBreak.Unix, Chars.Tab);
		Else
			Settings = New JSONWriterSettings(JSONLineBreak.None);
		EndIf;
		Writer.SetString(Settings);

		WriteJSON(Writer, Obj);

		S = Writer.Close();
	Except
		err = "JSON marshall: "+BriefErrorDescription(ErrorInfo());
	EndTry;

	Return S;

EndFunction // Marshall()

Function JSON_Unmarshall(S, Err=Undefined) Export

	Try
		Reader = New JSONReader;
		Reader.SetString(S);
		Obj =ReadJSON(Reader, true);
		Reader.Close();
	Except
		err = "JSON unmarshall: "+BriefErrorDescription(ErrorInfo());
		Return Undefined;
	EndTry;

	If TypeOf(Obj)<>Type("Map") AND TypeOf(Obj)<>Type("Array") AND TypeOf(Obj)<>Type("Structure") Then
		Err = "Not an object";
		Return Undefined;
	EndIf;

	Return Obj;

EndFunction // Unmarshall()

// Преобразует дату в строковое представление формата ISO 8601.
//
// Параметры:
//  Date - Дата - Дата для преобразования. Если передан не тип Дата, используется текущая дата.
//  Short - Булево - Если Истина, возвращает короткий формат "DD-MM-YYYY".
//  Delimiter - Строка - Разделитель между датой и временем. По умолчанию "T".
//
// Возвращаемое значение:
//  Строка - Дата в формате "YYYY-MM-DDThh:mm:ss" или "DD-MM-YYYY" (короткий формат).
//
Function JSON_DateToString(Date, Short=False, Delimiter=Undefined) Export

	If TypeOf(Date) <> Type("Date") Then

		Date = CurrentDate();

	EndIf;

	If Delimiter = Undefined Then

		Delimiter = "T";

	EndIf;

	Yar	= Format(Year(Date),"ND=4; NLZ=; NG=");
	Mon	= Format(Month(Date),"ND=2; NLZ=");
	Day	= Format(Day(Date),"ND=2; NLZ=");
	Hur	= Format(Hour(Date),"ND=2; NZ=00; NLZ=");
	Min	= Format(Minute(Date),"ND=2; NZ=00; NLZ=");
	Sec	= Format(Second(Date),"ND=2; NZ=00; NLZ=");

	If Short Then

		Return ""+Day+"-"+Mon+"-"+Yar;

	EndIf;

	Return ""+Yar+"-"+Mon+"-"+Day + Delimiter + Hur+":"+Min+":"+Sec;

EndFunction // DateToString()

// Преобразует строку даты в значение типа Дата.
// Поддерживаемые форматы: "YYYY-MM-DDThh:mm:ss", "YYYY-MM-DD hh:mm:ss", "YYYY-MM-DD".
// Часовой пояс (например "+03:00") игнорируется.
//
// Параметры:
//  SD - Строка - Строковое представление даты.
//
// Возвращаемое значение:
//  Дата - Результат преобразования. Дата "00010101" в случае некорректного формата.
//
Function JSON_DateFromString(SD)

	// замена пробела для поддержки формата "2024-09-14 03:00:00"
	If StrLen(SD) > 11 AND Mid(SD, 11, 1) = " " Then

		SD = StrReplace(SD, " ", "T");

	EndIf;

	If StrLen(SD) = 10 Then // support for short format '2024-09-14'

		Д = SD;
		В = "00:00:00";

	Else

		Если Найти(SD, "T") <> 11 ИЛИ СтрДлина(SD) < 19 Тогда

			Возврат Дата("00010101000000");

		КонецЕсли;

		Д = Сред(SD, 1, 10);
		В = Сред(SD, 12, 8);

	EndIf;

	Год=1; Месяц=1; День=1;
	Если СтрДлина(Д) = 10 Тогда
		Год = ОбщегоНазначения.ВернутьЧисло(Сред(Д,1,4));
		Месяц = ОбщегоНазначения.ВернутьЧисло(Сред(Д,6,2));
		День = ОбщегоНазначения.ВернутьЧисло(Сред(Д,9,2));
	КонецЕсли;

	Час=0; Мин=0; Сек=0;
	Если СтрДлина(В) = 8 Тогда
		Час = ОбщегоНазначения.ВернутьЧисло(Сред(В,1,2));
		Мин = ОбщегоНазначения.ВернутьЧисло(Сред(В,4,2));
		Сек = ОбщегоНазначения.ВернутьЧисло(Сред(В,7,2));
	КонецЕсли;

	Возврат Дата(Год,Месяц,День,Час,Мин,Сек);

EndFunction // DateFromString()

#EndRegion

#Region HTTP

Function HTTP_RequestWithJSON(Data, Err=Undefined) Export

	Err = Undefined;

	Body = JSON_Marshall(Data, Err);

	If Err <> Undefined Then

		Return Undefined;

	EndIf;

	Req = New HTTPRequest();
	Req.Headers.Insert("Content-type", "application/json;  charset=utf-8");
	Req.SetBodyFromString(Body, TextEncoding.UTF8, ByteOrderMarkUse.DontUse);

	Return Req;

EndFunction // RequestWithBody()

Function HTTP_NewConnection(Url, Port=0, SSL=True)

	If StrFind(Url, "https://") > 0 Then

		SSL = True;
		Url = StrReplace(Url, "https://", "");

	EndIf;

	If StrFind(Url, "http://") > 0 Then

		SSL = False;
		Url = StrReplace(Url, "http://", "");

	EndIf;

	Col = StrFind(Url, ":");
	If Col > 0 Then
		Port = Number(RemoveNonNumbers(Right(Url, Col)));
		Url = Left(Url, Col-1);
	EndIf;

	// Таймаут операций с соединением (сек). БЕЗ него зависшее соединение с relay
	// блокирует Get/Post/Put бессрочно: фоновое задание висит в активных, а в
	// журнале — тишина (код не возвращается из вызова, ретраи/Sleep не работают).
	// 6-й позиционный параметр HTTPConnection — Таймаут.
	Timeout = 60;

	If NOT SSL Then

		Return New HTTPConnection(Url, ?(Port=0, 80, Port),,,, Timeout);

	EndIf;

	Return New HTTPConnection(Url, ?(Port=0, 443, Port),,,, Timeout, New OpenSSLSecureConnection);

EndFunction // NewConnection()

Function HTTP_GET(Conn, Path="", Data=Undefined, StatusCode=0, Err=Undefined)

	Err = Undefined;

	StatusCode = 0;

	If TypeOf(Data) = Type("HTTPRequest") Then

		Req = Data;

	Else

		Req = New HTTPRequest();

		//Err = "Unsupported payload for GET request";
		//Return "";

	EndIf;

	If NOT IsBlankString(Path) Then

		Req.ResourceAddress = Path;

	EndIf;

	Try

		Resp = Conn.Get(Req);

	Except

		Err = ""+Path+": "+BriefErrorDescription(ErrorInfo());
		Return "";

	EndTry;

	StatusCode = Resp.StatusCode;

	Return Resp.GetBodyAsString();

EndFunction // GET()

Function HTTP_POST(Conn, Path, Data, StatusCode=0, Err=Undefined)

	Err = Undefined;

	StatusCode = 0;

	If TypeOf(Data) = Type("Structure") OR TypeOf(Data) = Type("Array") Then

		Req = HTTP_RequestWithJSON(Data, Err);

		If Err <> Undefined Then
			Return "";
		EndIf;

	ElsIf TypeOf(Data) = Type("HTTPRequest") Then

		Req = Data;

	Else

		Err = "Unsupported payload for POST request";
		Return "";

	EndIf;

	If NOT IsBlankString(Path) Then

		Req.ResourceAddress = Path;

	EndIf;

	Try

		Resp = Conn.POST(Req);

	Except

		Err = ""+Path+": "+BriefErrorDescription(ErrorInfo());
		Return "";

	EndTry;

	StatusCode = Resp.StatusCode;

	Return Resp.GetBodyAsString();

EndFunction // POST()

Function HTTP_DELETE(Conn, Path, Data=Undefined, StatusCode=0, Err=Undefined)

	Err = Undefined;

	StatusCode = 0;

	If TypeOf(Data) = Type("HTTPRequest") Then

		Req = Data;

	Else

		Req = New HTTPRequest();

	EndIf;

	If NOT IsBlankString(Path) Then

		Req.ResourceAddress = Path;

	EndIf;

	Try

		Resp = Conn.DELETE(Req);

	Except

		Err = ""+Path+": "+BriefErrorDescription(ErrorInfo());
		Return "";

	EndTry;

	StatusCode = Resp.StatusCode;

	Return Resp.GetBodyAsString();

EndFunction // DELETE()

#EndRegion

#Region DataRead

// Страница товаров для выгрузки.
// FilterKey / CursorKey позволяют вести независимую пагинацию по своему
// подмножеству товаров: товары и картинки в диф-обмене отбираются по разным
// спискам изменений (см. GetImageData).
Function GetProductPage(Params, Next, FilterKey = "FProducts", CursorKey = "ProductCursor")

	Data 		= New Array;
	PageSize 	= 100;

	// keyset-пагинация: страница = следующие PageSize записей после курсора
	// (Ref предыдущей страницы). Next=0 — первая страница (без курсора).
	// Устраняет O(N^2): каждая страница читает не более PageSize строк.
	If Next = 0 Then
		Cursor = Catalogs.Номенклатура.EmptyRef();
	Else
		Cursor = Params[CursorKey];
	EndIf;

	Filter = Params[FilterKey];

	Query = New Query;
	Query.Text =
	"SELECT TOP "+Format(PageSize, "NG=0")+"
	|	Номенклатура.Ref AS Ref
	|FROM
	|	Catalog.Номенклатура AS Номенклатура
	|WHERE
	|	NOT Номенклатура.DeletionMark
	|	AND NOT Номенклатура.НеВыгружатьНоменклатуру
	|	AND NOT Номенклатура.Ref IN HIERARCHY
	|				(SELECT
	|					Catalog.Номенклатура.Ref
	|				FROM
	|					Catalog.Номенклатура
	|				WHERE
	|					Catalog.Номенклатура.IsFolder
	|					AND Catalog.Номенклатура.НеВыгружатьНоменклатуру)
	|	AND CASE
	|			WHEN &WithFilter
	|				THEN Номенклатура.Ref IN (&Filter)
	|			ELSE TRUE
	|		END
	|	AND CASE
	|			WHEN &UseCursor
	|				THEN Номенклатура.Ref > &Cursor
	|			ELSE TRUE
	|		END
	|
	|ORDER BY
	|	Ref";

	Query.SetParameter("WithFilter", 	Filter<>Undefined);
	Query.SetParameter("Filter", 		Filter);
	Query.SetParameter("UseCursor", 	Next > 0);
	Query.SetParameter("Cursor", 		Cursor);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		Data.Add(Sel.Ref);
		LastRef = Sel.Ref;

	EndDo;

	If Data.Count() = 0 Then
		Next = 0;
	Else
		Next = Next + 1;
		Params.Insert(CursorKey, LastRef);
	EndIf;

	Return Data;

EndFunction // GetProductPage()

// Готовит данные о товарах (value_id = "item") и их ценах (value_id = "price").
//
// Остаток (quantity) сворачивается по всем организациям и по складам из
// СкладыДляВыгрузкиВ_МП — разреза здесь нет намеренно. При выключенной опции
// useStores приложение читает остаток именно из item.quantity, не спрашивая ни
// склад, ни фирму. Разрез включается опцией useStores (не useCompanies): тогда
// приложение переходит на таблицу rests и отбирает ее по паре company_guid +
// store_guid. Наборы "rest"/"store"/"company" мы не выгружаем, поэтому включать
// useStores нельзя — остатки на устройстве обнулятся.
Function GetProductData(Params, Next)

	Filter = GetProductPage(Params, Next);

	Data = New Array;

	Query = New Query;
	Query.Text =
	"SELECT
	|	ЦеныНоменклатурыSliceLast.Номенклатура AS Product,
	|	ЦеныНоменклатурыSliceLast.ВидЦен AS Type,
	|	ЦеныНоменклатурыSliceLast.ВидЦен.Description AS Description,
	|	ЦеныНоменклатурыSliceLast.Цена AS Price
	|FROM
	|	InformationRegister.ЦеныНоменклатуры.SliceLast(
	|			&Date,
	|			Номенклатура IN (&Filter)
	|				AND ВидЦен IN
	|					(SELECT
	|						Catalog.ВидыЦенМенеджеров.ВидЦен
	|					FROM
	|						Catalog.ВидыЦенМенеджеров
	|					WHERE
	|						Catalog.ВидыЦенМенеджеров.Owner = &Manager)) AS ЦеныНоменклатурыSliceLast
	|WHERE
	|	ЦеныНоменклатурыSliceLast.Цена > 0
	|
	|ORDER BY
	|	Product,
	|	Price";

	Query.SetParameter("Filter", 	Filter);
	Query.SetParameter("Date", 		CurrentDate());
	Query.SetParameter("Manager", 	Params.Manager);

	Prices = Query.Execute().Unload();

	Query = New Query;
	Query.Text =
	"SELECT
	|	Номенклатура.Ref AS Ref,
	|	Номенклатура.Parent AS Parent,
	|	Номенклатура.Description AS Description,
	|	Номенклатура.Артикул AS SKU,
	|	Номенклатура.Тон AS Tone,
	|	Номенклатура.Code AS Code,
	|	Номенклатура.ЕдиницаИзмерения AS Unit,
	|	Номенклатура.IsFolder AS IsFolder,
	|	Номенклатура.ШтУп AS Package,
	|	ISNULL(Inventory.Quantity, 0) AS Quantity
	|FROM
	|	Catalog.Номенклатура AS Номенклатура
	|		LEFT JOIN (SELECT
	|			ЗапасыНаСкладахBalance.Номенклатура AS Product,
	|			SUM(ЗапасыНаСкладахBalance.КоличествоBalance) AS Quantity
	|		FROM
	|			AccumulationRegister.ЗапасыНаСкладах.Balance(
	|					&Date,
	|					Номенклатура IN (&Filter)
	|						AND СтруктурнаяЕдиница IN
	|							(SELECT
	|								InformationRegister.СкладыДляВыгрузкиВ_МП.Склад
	|							FROM
	|								InformationRegister.СкладыДляВыгрузкиВ_МП)) AS ЗапасыНаСкладахBalance
	|
	|		GROUP BY
	|			ЗапасыНаСкладахBalance.Номенклатура) AS Inventory
	|		ON Номенклатура.Ref = Inventory.Product
	|WHERE
	|	Номенклатура.Ref IN(&Filter)";

	Query.SetParameter("Filter", Filter);
	Query.SetParameter("Date", CurrentDate());

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		UID = String(Sel.Ref.UUID());
		SKU = Sel.SKU;

		If NOT IsBlankString(Sel.Tone) Then

			SKU = StrTemplate("%1 (%2)", SKU, Sel.Tone);

		EndIf;

		// количество в упаковке строковое поле в справочнике
		Pkg = ?(ContainsNonNumbers(Sel.Package), 0, Number(Sel.Package));

		Item = New Structure;
		Item.Insert("value_id", 		"item");
		Item.Insert("guid", 			UID);
		Item.Insert("description",		Sel.Description);
		Item.Insert("vendor_code",		"");
		Item.Insert("code1", 			SKU); //этот код отображается в списке товаров на клиенте
		Item.Insert("code2", 			Sel.Code); //будет передан с устройства для идентификации элемента
		Item.Insert("sorting", 			RemoveNonNumbers(Sel.Code));
		Item.Insert("quantity", 		Sel.Quantity);
		Item.Insert("is_group",			?(Sel.IsFolder, 1, 0));
		Item.Insert("barcode",			"");
		Item.Insert("price", 			0);
		Item.Insert("min_price", 		0);
		Item.Insert("base_price", 		0);
		Item.Insert("package_only", 	0);
		Item.Insert("package_value", 	Pkg);
		Item.Insert("weight", 			0);
		Item.Insert("unit", 			String(Sel.Unit));
		Item.Insert("indivisible", 		1);

		If ValueIsFilled(Sel.Parent) Then

			Item.Insert("group_guid",	String(Sel.Parent.UUID()));

		EndIf;

		// --------------------------------------- выборка цен
		P = New Structure("Product", Sel.Ref);
		Rows = Prices.FindRows(P);

		If Rows.Count() > 0 Then

			PriceMax = 0;

			For Each Row In Rows Do

				PriceID = String(Row.Type.UUID());

				// закупочная цена
				If PriceID = "52afdcbb-6289-11e5-8e70-b2cafe37acb0" Then

					//Item.base_price = Row.Price;
					Continue;

				EndIf;

				PriceMax = Max(PriceMax, Row.Price);

				PriceItem = New Structure;
				PriceItem.Insert("value_id", 	"price");
				PriceItem.Insert("item_guid", 	UID);
				PriceItem.Insert("price_type", 	PriceID);
				PriceItem.Insert("price_name", 	Row.Description);
				PriceItem.Insert("price", 		Row.Price);

				Data.Add(PriceItem);

			EndDo;

			// цена по умолчанию
			Item.price = PriceMax;

		EndIf;

		Data.Add(Item);

	EndDo;

	Return Data;

EndFunction // GetProductData()

// Готовит данные о картинках товаров (value_id = "image") для страницы товаров.
// Источник URL:
//  - urlSitniksCRM, если заполнен (готовая публичная ссылка из CRM);
//  - иначе двоичные данные загружаются на relay-сервер (PUT images/{guid}),
//    используется URL из ответа; неизмененные файлы повторно не заливаются —
//    версия отслеживается в регистре сведений AV_UploadedImages.
// Контракт с бекендом — см. tasks/images-via-relay-plan.md.
// Ровно одна картинка товара помечается default=1 — только ее показывает
// приложение; приоритет: ФайлКартинкиСайт (ее показывает форма номенклатуры),
// затем ФайлКартинки, затем первая по дате создания.
Function GetImageData(Params, Next)

	Data = New Array;

	// собственный фильтр FImages: в диф-обмене картинки отправляются только для
	// товаров с изменившимися файлами, а не для всех попавших в диф товаров
	Filter = GetProductPage(Params, Next, "FImages", "ImageCursor");

	If Filter.Count() = 0 Then
		Return Data;
	EndIf;

	Query = New Query;
	Query.Text =
	"SELECT ALLOWED
	|	Files.Ref AS Ref,
	|	Files.ВладелецФайла AS Owner,
	|	Files.urlSitniksCRM AS URL,
	|	Files.Расширение AS Extension,
	|	Files.ДатаМодификацииУниверсальная AS Modified,
	|	Files.ДатаСоздания AS Created,
	|	CASE
	|		WHEN Files.ВладелецФайла.ФайлКартинкиСайт = Files.Ref
	|			THEN 0
	|		WHEN Files.ВладелецФайла.ФайлКартинки = Files.Ref
	|			THEN 1
	|		ELSE 2
	|	END AS DefaultOrder
	|FROM
	|	Catalog.НоменклатураПрисоединенныеФайлы AS Files
	|WHERE
	|	Files.ВладелецФайла IN(&Filter)
	|	AND NOT Files.DeletionMark
	|	AND (Files.urlSitniksCRM <> """"
	|			OR Files.Расширение IN (&Extensions))
	|
	|ORDER BY
	|	Owner,
	|	DefaultOrder,
	|	Created";

	// сравнение строк в запросе регистрозависимое — перечисляем оба варианта
	Extensions = New Array;
	Extensions.Add("jpg");
	Extensions.Add("JPG");
	Extensions.Add("jpeg");
	Extensions.Add("JPEG");
	Extensions.Add("png");
	Extensions.Add("PNG");
	Extensions.Add("webp");
	Extensions.Add("WEBP");

	Query.SetParameter("Filter", 		Filter);
	Query.SetParameter("Extensions", 	Extensions);

	Sel = Query.Execute().Select();

	CurrentOwner = Undefined;

	While Sel.Next() Do

		Modified 	= ?(ValueIsFilled(Sel.Modified), Sel.Modified, Sel.Created);
		VersionMs 	= ?(ValueIsFilled(Modified), (Modified - Date(1970, 1, 1)) * 1000, 0);

		If NOT IsBlankString(Sel.URL) Then

			// готовая публичная ссылка из Sitniks CRM
			Url = Sel.URL;

		Else

			Url = UploadedImageURL(Sel.Ref, Modified);

			If Url = Undefined Then

				Url = UploadImage(Params, Sel.Ref, Sel.Extension, VersionMs);

				If Params.Err <> Undefined Then
					Return Data;
				EndIf;

				If Url = Undefined Then
					// файл не удалось передать на сервер — картинку пропускаем
					Continue;
				EndIf;

				SaveUploadedImage(Sel.Ref, Modified, Url);

			EndIf;

			// ?v={time} уже включен сервером в URL — новая версия меняет URL,
			// приложение кэширует картинку по полному URL

		EndIf;

		// первая отправляемая строка товара — картинка по умолчанию
		Default = ?(Sel.Owner <> CurrentOwner, 1, 0);
		CurrentOwner = Sel.Owner;

		Item = New Structure;
		Item.Insert("value_id", 	"image");
		Item.Insert("item_guid", 	String(Sel.Owner.UUID()));
		Item.Insert("image_guid", 	String(Sel.Ref.UUID()));
		Item.Insert("default", 		Default);
		Item.Insert("time", 		VersionMs);
		Item.Insert("url", 			Url);
		Item.Insert("description", 	"");
		Item.Insert("type", 		"product");

		Data.Add(Item);

	EndDo;

	Return Data;

EndFunction // GetImageData()

// Возвращает URL картинки, ранее загруженной на relay-сервер,
// если сохраненная версия не старее текущей; иначе Неопределено.
Function UploadedImageURL(FileRef, Modified)

	Query = New Query;
	Query.Text =
	"SELECT
	|	Uploaded.VersionDate AS VersionDate,
	|	Uploaded.URL AS URL
	|FROM
	|	InformationRegister.AV_UploadedImages AS Uploaded
	|WHERE
	|	Uploaded.File = &File";

	Query.SetParameter("File", FileRef);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		If Sel.VersionDate >= Modified AND NOT IsBlankString(Sel.URL) Then
			Return Sel.URL;
		EndIf;

	EndDo;

	Return Undefined;

EndFunction // UploadedImageURL()

// Фиксирует факт загрузки картинки на relay-сервер.
Procedure SaveUploadedImage(FileRef, Modified, Url)

	Rec = InformationRegisters.AV_UploadedImages.CreateRecordManager();
	Rec.File 		= FileRef;
	Rec.VersionDate = Modified;
	Rec.UploadDate 	= CurrentDate();
	Rec.URL 		= Url;
	Rec.Write(True);

EndProcedure // SaveUploadedImage()

Function GetClientPage(Params, Next)

	Data 		= New Array;
	PageSize 	= 100;

	// keyset-пагинация (см. GetProductPage): страница = следующие PageSize
	// после курсора; устраняет O(N^2).
	If Next = 0 Then
		Cursor = Catalogs.Контрагенты.EmptyRef();
	Else
		Cursor = Params.ClientCursor;
	EndIf;

	Query = New Query;
	Query.Text =
	"SELECT TOP "+Format(PageSize, "NG=0")+"
	|	Контрагенты.Ref AS Ref
	|FROM
	|	Catalog.Контрагенты AS Контрагенты
	|WHERE
	|	NOT Контрагенты.DeletionMark
	|	AND Контрагенты.Ответственный = &Manager
	|	AND CASE
	|			WHEN &WithFilter
	|				THEN Контрагенты.Ref IN (&Filter)
	|			ELSE TRUE
	|		END
	|	AND CASE
	|			WHEN &UseCursor
	|				THEN Контрагенты.Ref > &Cursor
	|			ELSE TRUE
	|		END
	|
	|ORDER BY
	|	Ref";

	Query.SetParameter("Manager", 		Params.Manager);
	Query.SetParameter("WithFilter", 	Params.FClients<>Undefined);
	Query.SetParameter("Filter", 		Params.FClients);
	Query.SetParameter("UseCursor", 	Next > 0);
	Query.SetParameter("Cursor", 		Cursor);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		Data.Add(Sel.Ref);
		LastRef = Sel.Ref;

	EndDo;

	If Data.Count() = 0 Then
		Next = 0;
	Else
		Next = Next + 1;
		Params.Insert("ClientCursor", LastRef);
	EndIf;

	Return Data;

EndFunction // GetClientPage()

// Работа с несколькими фирмами. Определяет одноименную опцию устройства и
// разрез по организациям в выгрузке взаиморасчетов (см. GetDebtData):
// при Ложь долги сворачиваются по всем организациям и уходят с пустым
// company_guid — так же, как приложение их запрашивает при выключенной опции.
Function UseCompanies() Export

	Return False;

EndFunction // UseCompanies()

// GUID организации для выгрузки; пустая ссылка — пустая строка,
// приложение при выключенной опции useCompanies ищет записи именно по ней.
Function CompanyGuid(Company)

	Return ?(ValueIsFilled(Company), String(Company.UUID()), "");

EndFunction // CompanyGuid()

// Текущий баланс взаиморасчетов по списку контрагентов.
// Знак: плюс — долг клиента, минус — оплата/аванс.
// Разрез по организации — только при включенной опции UseCompanies();
// иначе Company во всех строках пустая ссылка.
// Возвращает ТаблицуЗначений с колонками Client, Company, Sum.
Function ClientBalances(Clients)

	Query = New Query;
	Query.Text =
	"SELECT ALLOWED
	|	Balances.Контрагент AS Client,
	|	CASE
	|		WHEN &ByCompany
	|			THEN Balances.Организация
	|		ELSE VALUE(Catalog.Организации.EmptyRef)
	|	END AS Company,
	|	SUM(CASE
	|			WHEN Balances.ТипРасчетов = VALUE(Enum.ТипыРасчетов.Долг)
	|				THEN Balances.СуммаОстаток
	|			ELSE -Balances.СуммаОстаток
	|		END) AS Sum
	|FROM
	|	AccumulationRegister.РасчетыСПокупателями.Balance(, Контрагент IN (&Clients)) AS Balances
	|
	|GROUP BY
	|	Balances.Контрагент,
	|	CASE
	|		WHEN &ByCompany
	|			THEN Balances.Организация
	|		ELSE VALUE(Catalog.Организации.EmptyRef)
	|	END";

	Query.SetParameter("Clients", 	Clients);
	Query.SetParameter("ByCompany", UseCompanies());

	Return Query.Execute().Unload();

EndFunction // ClientBalances()

Function GetClientData(Params, Next)

	Data = New Array;

	TPhone = Catalogs.ВидыКонтактнойИнформации.ТелефонКонтрагента;
	TAddress = Catalogs.ВидыКонтактнойИнформации.АдресДоставкиКонтрагета;

	Clients = GetClientPage(Params, Next);

	If Clients.Count() = 0 Then
		Return Data;
	EndIf;

	Query = New Query;
	Query.Text =
	"SELECT
	|	Контрагенты.Ref AS Ref,
	|	Контрагенты.IsFolder AS IsFolder,
	|	Контрагенты.Code AS Code,
	|	Контрагенты.Parent AS Parent,
	|	Контрагенты.Description AS Description,
	|	ISNULL(Контрагенты.ДоговорПоУмолчанию.ВидЦен, VALUE(Catalog.ВидыЦен.EmptyRef)) AS PriceType,
	|	Контрагенты.КонтактнаяИнформация.(
	|		Вид,
	|		Представление,
	|		АдресЭП,
	|		НомерТелефона
	|	)
	|FROM
	|	Catalog.Контрагенты AS Контрагенты
	|WHERE
	|	Контрагенты.Ref IN(&Filter)
	|	AND NOT Контрагенты.IsFolder";

	Query.SetParameter("Filter", Clients);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		Item = New Structure;
		Item.Insert("value_id", 		"client");
		Item.Insert("guid", 			String(Sel.Ref.UUID()));
		Item.Insert("description",		Sel.Description);
		Item.Insert("code1", 			Sel.Code); //этот код отображается в списке товаров на клиенте
		Item.Insert("code2", 			Sel.Code); //будет передан с устройства для идентификации элемента
		Item.Insert("is_group",			?(Sel.IsFolder, 1, 0));
		Item.Insert("phone",			"");
		Item.Insert("address", 			"");
		Item.Insert("discount", 		0);
		// у контрагента может не быть договора по умолчанию или вида цен в нем
		Item.Insert("price_type", 		?(ValueIsFilled(Sel.PriceType), String(Sel.PriceType.UUID()), ""));
		// баланс клиента здесь не передается: приложение берет его из набора
		// "debt" — итоговой строки с пустым doc_id (см. GetDebtData)

		//If ValueIsFilled(Sel.Parent) Then
		//
		//	Item.Insert("group_guid",	String(Sel.Parent.UUID()));
		//
		//EndIf;

		Contacts = Sel.КонтактнаяИнформация.Select();

		While Contacts.Next() Do

			If Contacts.Вид = TPhone Then

				Item.phone = Contacts.НомерТелефона;

			ElsIf Contacts.Вид = TAddress Then

				Item.address = Contacts.Представление;

			EndIf;

		EndDo;

		Data.Add(Item);

	EndDo;

	Return Data;

EndFunction // GetClientData()

Function GetDiscountData(Params, Next)

	Data = New Array;

	Query = New Query;
	Query.Text =
	"SELECT
	|	СкидкиНаценкиНоменклатуры.Owner AS Client,
	|	СкидкиНаценкиНоменклатуры.Номенклатура AS Product,
	|	СкидкиНаценкиНоменклатуры.СкидкаНаценка AS Discount
	|FROM
	|	Catalog.СкидкиНаценкиНоменклатуры AS СкидкиНаценкиНоменклатуры
	|WHERE
	|	СкидкиНаценкиНоменклатуры.Owner IN(&Owner)
	|	AND СкидкиНаценкиНоменклатуры.СкидкаНаценка <> 0
	|	AND NOT СкидкиНаценкиНоменклатуры.Номенклатура.DeletionMark";

	Query.SetParameter("Owner", GetClientPage(Params, Next));

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		Item = New Structure;
		Item.Insert("value_id", 		"discount");
		Item.Insert("client_guid", 		String(Sel.Client.UUID()));
		Item.Insert("item_guid",		String(Sel.Product.UUID()));
		Item.Insert("discount",			Sel.Discount);  // скидка отрицательная, наценка положительная

		Data.Add(Item);

	EndDo;

	Return Data;

EndFunction // GetDiscountData()

// Готовит данные о взаиморасчетах (value_id = "debt") для страницы клиентов:
//  - итоговая строка по каждому клиенту (пустой doc_id) — приложение показывает
//    ее сумму как баланс клиента; шлем всем клиентам страницы, даже нулевую,
//    иначе прежний долг останется на устройстве;
//  - детальные строки по документам-регистраторам за последние N дней.
// Знак: плюс — долг клиента, минус — оплата/аванс.
//
// company_guid заполняется только при включенной опции UseCompanies(): приложение
// отбирает долги по выбранной на устройстве фирме, а при выключенной опции фирма
// там пустая — поэтому долги сворачиваются по всем организациям и уходят с "".
Function GetDebtData(Params, Next)

	Data = New Array;

	Clients = GetClientPage(Params, Next);

	If Clients.Count() = 0 Then
		Return Data;
	EndIf;

	// период выгрузки движений взаиморасчетов, дней
	PeriodDays 	= 90;
	DateTo 		= EndOfDay(CurrentDate());
	DateFrom 	= BegOfDay(DateTo - PeriodDays * 86400);

	// -------------------------------------------------- текущий баланс клиентов
	Balance = ClientBalances(Clients);

	// клиенты, по которым баланса нет: им нужна нулевая итоговая строка,
	// иначе на устройстве останется долг с прошлого обмена
	WithBalance = New Map;

	For Each Row In Balance Do

		WithBalance.Insert(Row.Client, True);

		Item = New Structure;
		Item.Insert("value_id", 	"debt");
		Item.Insert("client_guid", 	String(Row.Client.UUID()));
		Item.Insert("company_guid", CompanyGuid(Row.Company));
		Item.Insert("doc_id", 		"");
		Item.Insert("doc_guid", 	"");
		Item.Insert("doc_type", 	"");
		Item.Insert("sum", 			Row.Sum);
		Item.Insert("sorting", 		0);
		Item.Insert("has_content", 	0);

		Data.Add(Item);

	EndDo;

	For Each Client In Clients Do

		If WithBalance.Get(Client) <> Undefined Then
			Continue;
		EndIf;

		Item = New Structure;
		Item.Insert("value_id", 	"debt");
		Item.Insert("client_guid", 	String(Client.UUID()));
		Item.Insert("company_guid", "");
		Item.Insert("doc_id", 		"");
		Item.Insert("doc_guid", 	"");
		Item.Insert("doc_type", 	"");
		Item.Insert("sum", 			0);
		Item.Insert("sorting", 		0);
		Item.Insert("has_content", 	0);

		Data.Add(Item);

	EndDo;

	// -------------------------------------------------- движения по документам
	Query = New Query;
	Query.Text =
	"SELECT ALLOWED
	|	Turnovers.Контрагент AS Client,
	|	CASE
	|		WHEN &ByCompany
	|			THEN Turnovers.Организация
	|		ELSE VALUE(Catalog.Организации.EmptyRef)
	|	END AS Company,
	|	Turnovers.Recorder AS Doc,
	|	Turnovers.Recorder.Date AS DocDate,
	|	SUM(CASE
	|			WHEN Turnovers.ТипРасчетов = VALUE(Enum.ТипыРасчетов.Долг)
	|				THEN Turnovers.СуммаПриход - Turnovers.СуммаРасход
	|			ELSE Turnovers.СуммаРасход - Turnovers.СуммаПриход
	|		END) AS Sum
	|FROM
	|	AccumulationRegister.РасчетыСПокупателями.Turnovers(&DateFrom, &DateTo, Recorder, Контрагент IN (&Clients)) AS Turnovers
	|
	|GROUP BY
	|	Turnovers.Контрагент,
	|	CASE
	|		WHEN &ByCompany
	|			THEN Turnovers.Организация
	|		ELSE VALUE(Catalog.Организации.EmptyRef)
	|	END,
	|	Turnovers.Recorder,
	|	Turnovers.Recorder.Date
	|
	|ORDER BY
	|	Client,
	|	DocDate";

	Query.SetParameter("Clients", 	Clients);
	Query.SetParameter("DateFrom", 	DateFrom);
	Query.SetParameter("DateTo", 	DateTo);
	Query.SetParameter("ByCompany", UseCompanies());

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		If Sel.Sum = 0 Then
			Continue;
		EndIf;

		Item = New Structure;
		Item.Insert("value_id", 	"debt");
		Item.Insert("client_guid", 	String(Sel.Client.UUID()));
		Item.Insert("company_guid", CompanyGuid(Sel.Company));
		// doc_id отображается в приложении как есть и входит в ключ записи;
		// дата видна только внутри представления документа
		Item.Insert("doc_id", 		TrimAll(String(Sel.Doc)));
		Item.Insert("doc_guid", 	String(Sel.Doc.UUID()));
		Item.Insert("doc_type", 	Sel.Doc.Metadata().Name);
		Item.Insert("sum", 			Sel.Sum);
		// сортировка по дате документа (секунды от 2001 года),
		// приложение упорядочивает по возрастанию
		Item.Insert("sorting", 		Sel.DocDate - Date(2001, 1, 1));
		// расшифровка документов через relay-сервер не поддерживается
		Item.Insert("has_content", 	0);

		Data.Add(Item);

	EndDo;

	Return Data;

EndFunction // GetDebtData()

#EndRegion

#Region DataUpload

Procedure DeviceDataUpload(Params) Export

	// метка времени в миллисекундах для отметки всех элементов данных
	Params.Insert("Timestamp", CurrentUniversalDateInMilliseconds());

	// заглушки для фильтров по изменениям
	Params.Insert("FProducts", 	Undefined);
	Params.Insert("FImages", 	Undefined);
	Params.Insert("FClients", 	Undefined);

	UserOptions(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	SendProducts(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	SendImages(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	SendClients(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	SendDiscounts(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	SendDebts(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	FinishFullUpload(Params);

EndProcedure

Procedure DeviceOptionsUpload(Params) Export

	// метка времени в миллисекундах для отметки всех элементов данных
	Params.Insert("Timestamp", 	CurrentUniversalDateInMilliseconds());
	Params.Insert("Conn", 		Undefined);

	UserOptions(Params);

	Params.Insert("Conn", 		Undefined);

EndProcedure

// Опции (value_id "options") в этом проекте фиксированы намеренно (см. AV_Settings/[[av-options-and-diff]]):
// нет per-пользовательской настройки, значения захардкожены здесь. Правило только для текущей интеграции.
// Выгружаются ТОЛЬКО при полной выгрузке (вызов из DeviceDataUpload) или вручную (DeviceOptionsUpload) —
// плановый дифф-обмен (DiffUpload) options не пересобирает и не шлёт. Если поменять значение опции здесь,
// устройство увидит его только на следующей полной выгрузке для этого device_uuid.
Procedure UserOptions(Params)

	Валюта = PredefinedValue("Catalog.Валюты.УдалитьНациональнаяВалюта");
	ИмяВалюты = Строка(Валюта);

	Opt = New Structure;
	Opt.Insert("value_id",				"options");
	Opt.Insert("name",					"Unknown");
	Opt.Insert("read",					True);
	Opt.Insert("write",					True);
	Opt.Insert("currency",				ИмяВалюты);
	Opt.Insert("allowReturn",			False); // разрешение на ввод документа на возврат товара
	// геолокация
	Opt.Insert("locations",				False);
	Opt.Insert("clientsLocations",		False);
	Opt.Insert("editLocations",			False);
	Opt.Insert("lastLocationTime",		0);
	Opt.Insert("checkOrderLocation",	False);
	// печать
	Opt.Insert("printingEnabled",		False);
	// картинки
	Opt.Insert("loadImages",			True);
	// лицензия
	Opt.Insert("license","");
	// FCM токен
	Opt.Insert("sendPushToken",			False);
	// тип оплаты
	Opt.Insert("allowPaymentType",		False);
	Opt.Insert("defaultPaymentType",	"");
	// работа с ценами
	Opt.Insert("allowPriceTypeChoose",	False);
	Opt.Insert("allowPriceEdit",		True);
	Opt.Insert("showClientPriceOnly",	True);
	Opt.Insert("setClientPrice",		True);
	// система скидок - простая (скидка клиента), сложная (скидка в разрезе клиентов и товаров)
	Opt.Insert("complexDiscounts",		True);
	// выгрузка в режиме "только изменения"
	Opt.Insert("differentialUpdates",	False);
	// клиент по умолчанию
	Opt.Insert("defaultClient", 		"");
	// работа с несколькими фирмами
	Opt.Insert("useCompanies", 			UseCompanies());
	// работа с несколькими складами; включать только вместе с выгрузкой
	// наборов "store"/"rest"/"company" (см. GetProductData)
	Opt.Insert("useStores", 			False);

	// --- дополнительные распознаваемые приложением опции (п.20) ---
	// требовать дату доставки в заказе
	Opt.Insert("requireDeliveryDate",	False);
	// матрицы клиентов: маршруты и товары (данные пока не выгружаются)
	Opt.Insert("clientsDirections",		False);
	Opt.Insert("clientsProducts",		False);
	// флаги подборщика: потребность и маркировка упаковки
	Opt.Insert("useDemands",			False);
	Opt.Insert("usePackageMark",		False);
	// журнал отладки на устройстве
	Opt.Insert("debugLogsEnabled",		False);


	Name = Params.Name;
	If IsBlankString(Name) Then
		Name = String(Params.Manager);
	EndIf;
	Opt.Insert("name", Name);

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	// если задать ид сообщения, оно перезапишет предыдущее сообщение с этим ид
	Data.Insert("message_uuid", Params.DeviceID);

	// ключ "data" по контракту (единообразно с остальными push-запросами)
	Data.Insert("data", New Array);
	Data.data.Add(Opt);

	DoRequest(Params, "push", Data);

EndProcedure

Procedure SendProducts(Params)

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	Count = 0;
	Num = 0;

	While 1=1 Do

		Items = GetProductData(Params, Num);

		If Items.Count()>0 Then

			Count = Count + Items.Count();

			//Message("... products portion: " + Count);

			Data.Insert("data", Items);

			DoRequest(Params, "push", Data);

		EndIf;

		If Params.Err<>Undefined Then
			Return;
		EndIf;

		If Num=0 Then
			Return;
		EndIf;

	EndDo;

EndProcedure

Procedure SendImages(Params)

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	Num = 0;

	While 1=1 Do

		Items = GetImageData(Params, Num);

		If Items.Count()>0 Then

			Data.Insert("data", Items);

			DoRequest(Params, "push", Data);

		EndIf;

		If Params.Err<>Undefined Then
			Return;
		EndIf;

		If Num=0 Then
			Return;
		EndIf;

	EndDo;

EndProcedure

Procedure SendClients(Params)

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	Num = 0;

	While 1=1 Do

		Items = GetClientData(Params, Num);

		If Items.Count()>0 Then

			//Message("... clients portion: " + Items.Count());

			Data.Insert("data", Items);

			DoRequest(Params, "push", Data);

		EndIf;

		If Params.Err<>Undefined Then
			Return;
		EndIf;

		If Num=0 Then
			Return;
		EndIf;

	EndDo;

EndProcedure

Procedure SendDiscounts(Params)

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	Num = 0;

	While 1=1 Do

		Items = GetDiscountData(Params, Num);

		If Items.Count()>0 Then

			Data.Insert("data", Items);

			DoRequest(Params, "push", Data);

		EndIf;

		If Params.Err<>Undefined Then
			Return;
		EndIf;

		If Num=0 Then
			Return;
		EndIf;

	EndDo;

EndProcedure

Procedure SendDebts(Params)

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	Num = 0;

	While 1=1 Do

		Items = GetDebtData(Params, Num);

		If Items.Count()>0 Then

			Data.Insert("data", Items);

			DoRequest(Params, "push", Data);

		EndIf;

		If Params.Err<>Undefined Then
			Return;
		EndIf;

		If Num=0 Then
			Return;
		EndIf;

	EndDo;

EndProcedure

//Если выгрузка прошла успешно, отсылаем метку когда выгрузка началась
//на телефоне будут удалены все данные до этой метки
Procedure FinishFullUpload(Params)

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);
	Data.Insert("timestamp", Params.Timestamp);

	DoRequest(Params, "push/complete", Data);

EndProcedure

// Однократно читает регистрации изменений плана обмена и раскладывает их по картам.
// Результат пригоден для повторного использования всеми устройствами, разделяющими
// один план обмена (devices одной AV_Settings).
//
// Возвращает структуру:
//   Objects     — Array(СправочникОбъект/НаборЗаписей) — для последующей очистки регистраций
//   Products    — Array(СправочникСсылка.Номенклатура) — фильтр для SendProducts
//   Images      — Array(СправочникСсылка.Номенклатура) — фильтр для SendImages
//   Clients     — Array(СправочникСсылка.Контрагенты)  — фильтр для SendClients / SendDiscounts / SendDebts
//   AllProducts — Булево — изменился состав видов цен менеджера, нужен полный переслап товаров
//
// Products и Images намеренно разделены: движения остатков и цен затрагивают
// почти весь ассортимент при каждом обмене, а картинки при этом не меняются.
//
Function FetchDiffChanges(Node) Export

	Changes = New Structure;
	Changes.Insert("Objects",     New Array);
	Changes.Insert("Products",    New Array);
	Changes.Insert("Images",      New Array);
	Changes.Insert("Clients",     New Array);
	Changes.Insert("AllProducts", False);

	If NOT ValueIsFilled(Node) Then
		Return Changes;
	EndIf;

	// Карты используются как множества для O(1) проверки дубликатов
	ProductsMap = New Map;
	ImagesMap   = New Map;
	ClientsMap  = New Map;

	Sel = ExchangePlans.SelectChanges(Node, Node.НомерОтправленного+1);

	While Sel.Next() Do

		Obj = Sel.Get();
		Type = TypeOf(Obj);

		If Type = Type("СправочникОбъект.Номенклатура") Then

			If ProductsMap.Get(Obj.Ref) = Undefined Then
				ProductsMap.Insert(Obj.Ref, True);
				Changes.Products.Add(Obj.Ref);
			EndIf;
			// реквизиты ФайлКартинкиСайт / ФайлКартинки задают картинку по
			// умолчанию, поэтому изменение товара переотправляет и его картинки
			If ImagesMap.Get(Obj.Ref) = Undefined Then
				ImagesMap.Insert(Obj.Ref, True);
				Changes.Images.Add(Obj.Ref);
			EndIf;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("РегистрНакопленияНаборЗаписей.ЗапасыНаСкладах") Then

			Для каждого Запись Из Obj Do
				If ProductsMap.Get(Запись.Номенклатура) = Undefined Then
					ProductsMap.Insert(Запись.Номенклатура, True);
					Changes.Products.Add(Запись.Номенклатура);
				EndIf;
			EndDo;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("РегистрСведенийНаборЗаписей.ЦеныНоменклатуры") Then

			Для каждого Запись Из Obj Do
				If ProductsMap.Get(Запись.Номенклатура) = Undefined Then
					ProductsMap.Insert(Запись.Номенклатура, True);
					Changes.Products.Add(Запись.Номенклатура);
				EndIf;
			EndDo;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("СправочникОбъект.НоменклатураПрисоединенныеФайлы") Then

			// изменился файл — переотправляем картинки товара, сам товар не тронут
			If ImagesMap.Get(Obj.ВладелецФайла) = Undefined Then
				ImagesMap.Insert(Obj.ВладелецФайла, True);
				Changes.Images.Add(Obj.ВладелецФайла);
			EndIf;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("РегистрНакопленияНаборЗаписей.РасчетыСПокупателями") Then

			Для каждого Запись Из Obj Do
				If ClientsMap.Get(Запись.Контрагент) = Undefined Then
					ClientsMap.Insert(Запись.Контрагент, True);
					Changes.Clients.Add(Запись.Контрагент);
				EndIf;
			EndDo;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("СправочникОбъект.СкидкиНаценкиНоменклатуры") Then

			If ClientsMap.Get(Obj.Owner) = Undefined Then
				ClientsMap.Insert(Obj.Owner, True);
				Changes.Clients.Add(Obj.Owner);
			EndIf;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("СправочникОбъект.Контрагенты") Then

			If ClientsMap.Get(Obj.Ref) = Undefined Then
				ClientsMap.Insert(Obj.Ref, True);
				Changes.Clients.Add(Obj.Ref);
			EndIf;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("СправочникОбъект.ДоговорыКонтрагентов") Then

			If ClientsMap.Get(Obj.Owner) = Undefined Then
				ClientsMap.Insert(Obj.Owner, True);
				Changes.Clients.Add(Obj.Owner);
			EndIf;
			Changes.Objects.Add(Obj);

		ElsIf Type = Type("СправочникОбъект.ВидыЦенМенеджеров") Then

			// изменение состава видов цен менеджера затрагивает цены всех товаров —
			// помечаем на полный переслап товаров с ценами
			Changes.AllProducts = True;
			Changes.Objects.Add(Obj);

		Else

			// прочее — не отправляется, но регистрации очищаются вместе с группой
			Changes.Objects.Add(Obj);

		EndIf;

	EndDo;

	Return Changes;

EndFunction

Procedure DiffUpload(Params, Changes) Export

	Node = Params.ExchangePlan;

	// если план не указан, тихо выходим, выгрузка изменений не поддерживается
	If NOT ValueIsFilled(Node) Then
		Return;
	EndIf;

	// нечего отправлять
	If Changes.Objects.Count() = 0 Then
		Params.Insert("DiffObjectsToCleanup", Changes.Objects);
		Return;
	EndIf;

	// метка времени в миллисекундах для отметки всех элементов данных
	Params.Insert("Timestamp", CurrentUniversalDateInMilliseconds());

	If Changes.AllProducts Then

		// изменился состав видов цен менеджера — пересылаем все товары с ценами;
		// без push/complete это upsert на устройстве, удалений не будет.
		// Картинок это не касается — они отправляются отдельно, по своему списку
		Params.Insert("FProducts", Undefined);

		SendProducts(Params);

		If Params.Err <> Undefined Then
			Return;
		EndIf;

	ElsIf Changes.Products.Count() > 0 Then

		Params.Insert("FProducts", Changes.Products);

		SendProducts(Params);

		If Params.Err <> Undefined Then
			Return;
		EndIf;

	EndIf;

	If Changes.Images.Count() > 0 Then

		Params.Insert("FImages", Changes.Images);

		SendImages(Params);

		If Params.Err <> Undefined Then
			Return;
		EndIf;

	EndIf;

	If Changes.Clients.Count() > 0 Then

		Params.Insert("FClients", Changes.Clients);

		SendClients(Params);

		If Params.Err <> Undefined Then
			Return;
		EndIf;

		SendDiscounts(Params);

		If Params.Err <> Undefined Then
			Return;
		EndIf;

		SendDebts(Params);

		If Params.Err <> Undefined Then
			Return;
		EndIf;

	EndIf;

	// Список изменений, накопленных для текущего плана обмена.
	// Очистка регистраций выполняется в ExchangeJob после успешной обработки
	// всех устройств, привязанных к этому же плану (AV_Settings.Ref).
	Params.Insert("DiffObjectsToCleanup", Changes.Objects);

EndProcedure

#EndRegion

#Region DataDownload

Procedure DeviceDataDownload(Params) Export

	// Вычитываем очередь циклом до опустошения в пределах одного прогона.
	// После reserve+lease на бекенде уже выданные (но не подтверждённые) строки
	// исключены из выборки на время аренды, поэтому цикл дренирует новые
	// документы и не зацикливается на упавших. MaxBatches — предохранитель.
	PullLimit  = 100;   // максимум, разрешённый бекендом (1..100)
	MaxBatches = 100;   // до 100 пачек за прогон (предохранитель от зацикливания)
	Path       = StrTemplate("pull?limit=%1", PullLimit);

	For BatchNo = 1 To MaxBatches Do

		GET(Params, Path);

		If Params.Err<>Undefined Then
			Return;
		EndIf;

		If Params.Response = Undefined Then
			Params.Err = "Empty response";
			Return;
		EndIf;

		Data  = Params.Response["data"];
		Count = Data["count"];

		If Count = Undefined OR Count = 0 Then
			If BatchNo = 1 Then
				Message("Device data queue is EMPTY");
			EndIf;
			Return;
		EndIf;

		Message("Received data elements: "+Count);

		ProcessPullBatch(Data, Params);

		// пачка неполная -> доступная очередь исчерпана в этом прогоне
		If Count < PullLimit Then
			Return;
		EndIf;

	EndDo;

	// достигнут предел пачек: остаток заберём на следующем прогоне задания
	WriteLogEvent("AgentVenta", EventLogLevel.Warning,,,
		StrTemplate("pull: достигнут предел %1 пачек за прогон; остаток очереди — на следующий запуск", MaxBatches));

EndProcedure

// Обрабатывает одну пачку очереди pull: загружает документы и подтверждает
// (pull/ack) успешно обработанные.
Procedure ProcessPullBatch(Data, Params)

	// токен пачки для подтверждения обработанных документов (pull/ack).
	// Пусто, если бекенд ещё в режиме claim-on-read (тогда ack не нужен).
	FetchToken = Data["fetch_token"];

	Items    = Data["items"];
	AckGuids = New Array;
	AckIds   = New Array;

	For Each Item In Items Do

		DataType  = Item["data_type"];
		Payload   = Item["data"];
		Processed = False;

		If DataType = "order" Then

			Processed = DownloadOrder(Payload, Params);

		ElsIf DataType = "cash" Then

			Processed = DownloadCash(Payload, Params);

		Else

			// неподдерживаемый тип не подтверждаем: пусть остаётся в очереди
			// (бекенд ограничит число выдач и переведёт в dead-letter)
			Message("Unsupported data type: "+DataType);

		EndIf;

		// подтверждаем только обработанные документы; остальные придут повторно.
		// Возвращаем эхом ключи бекенда: приоритет document_guid, иначе id
		// строки очереди (для payload без document_guid).
		If Processed Then

			DocGuid = Item["document_guid"];

			If NOT IsBlankString(DocGuid) Then
				AckGuids.Add(DocGuid);
			Else
				MsgId = Item["id"];
				If NOT IsBlankString(MsgId) Then
					AckIds.Add(MsgId);
				EndIf;
			EndIf;

		EndIf;

	EndDo;

	AckPulled(Params, FetchToken, AckGuids, AckIds);

EndProcedure

// Подтверждает бекенду успешно обработанные документы очереди pull:
// POST /api/v1/pull/ack {fetch_token, document_guids:[...], message_ids:[...]}.
// Мягкая: любая ошибка (в т.ч. 404, если бекенд ещё без ack-эндпоинта) только
// пишется в лог и НЕ прерывает обмен (Params.Err не выставляется).
// Неподтверждённые документы будут выданы бекендом повторно.
Procedure AckPulled(Params, FetchToken, Guids, Ids)
	Var Err, StatusCode;

	If Guids.Count() = 0 AND Ids.Count() = 0 Then
		Return;
	EndIf;

	// нет токена пачки -> бекенд в режиме claim-on-read, подтверждать нечего
	If IsBlankString(FetchToken) Then
		Return;
	EndIf;

	Data = New Structure;
	Data.Insert("fetch_token", FetchToken);

	If Guids.Count() > 0 Then
		Data.Insert("document_guids", Guids);
	EndIf;

	If Ids.Count() > 0 Then
		Data.Insert("message_ids", Ids);
	EndIf;

	Req = HTTP_RequestWithJSON(Data, Err);

	If Err <> Undefined Then
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,,,
			StrTemplate("pull/ack prepare failed: %1", Err));
		Return;
	EndIf;

	If Params.Conn = Undefined Then
		Params.Conn = ServerConnection(Params);
	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = "/api/v1/pull/ack";

	HTTP_POST(Params.Conn, , Req, StatusCode, Err);

	If Err <> Undefined Then
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,,,
			StrTemplate("pull/ack request failed: %1; status=%2", Err, StatusCode));
		Return;
	EndIf;

	If StatusCode <> 200 Then
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,,,
			StrTemplate("pull/ack not confirmed: status=%1", StatusCode));
		Return;
	EndIf;

EndProcedure

// Загружает заказ из очереди pull в документ ЗаказПокупателя.
// Возвращает Истина, если документ можно подтвердить бекенду (pull/ack):
// успешно записан/проведён, уже проведён ранее (идемпотентный повтор) или
// осознанно отброшен как неисправимый (некорректный guid). Возвращает Ложь для
// исправимых сбоев (неизвестное устройство, ошибка записи/проведения) — такой
// документ будет выдан повторно.
Function DownloadOrder(Order, Params)

	// дата документа указана в данных в виде timestamp в секундах
	// нужно посчитать от начала эпохи Unix
	BaseDate = Date(1970, 1, 1, 0, 0, 0);

	Manager = GetManager(Order["userID"]);

	If Manager = Undefined Then

		Txt = "Невідомий пристрій, документ пропущений; ID=" + Order["userID"];

		WriteLogEvent("AgentVenta", EventLogLevel.Error,,, Txt);
		Message(Txt);
		Return False;

	EndIf;

	///////////////////////////////////////////// ссылка на документ
	UID = Order["guid"];

	Try

		Ref = Documents.ЗаказПокупателя.GetRef(New UUID(UID));

	Except
	    Message(StrTemplate("id=%1; %2", UID, BriefErrorDescription(ErrorInfo())));
		Return True;
	EndTry;

	Doc = Ref.GetObject();

	///////////////////////////////////////////// создание нового документа
	If Doc = Undefined Then

		Doc = Documents.ЗаказПокупателя.CreateDocument();
		Doc.SetNewObjectRef(Ref);

		Doc.ВидОперации = Перечисления.ВидыОперацийЗаказПокупателя.ЗаказНаПродажу;
		Doc.Ответственный = Manager;

		УправлениеНебольшойФирмойСервер.ЗаполнитьШапкуДокумента(
			Doc,
			Doc.ВидОперации,
			,,,,,
		);

		// организация из приложения (company_guid), если элемент существует;
		// иначе остаётся значение по умолчанию из ЗаполнитьШапкуДокумента
		ОрганизацияЗаказа = ResolveCatalogRef(Справочники.Организации, Order["company_guid"]);
		Если ОрганизацияЗаказа <> Неопределено Тогда
			Doc.Организация = ОрганизацияЗаказа;
		КонецЕсли;

		// в данных документа есть время создания и время редактирования
		// time - когда создан
		// time_saved - когда записаны последние изменения
		Doc.Date = BaseDate + Order["time_saved"];

		Если НЕ ЗначениеЗаполнено(Doc.Date) Тогда
			Doc.Date = ТекущаяДата();
		КонецЕсли;

		ДатаОтгрузки = Doc.ДатаОтгрузки;
		Если НЕ ЗначениеЗаполнено(ДатаОтгрузки) Тогда
			Doc.ДатаОтгрузки = Doc.Date;
		КонецЕсли;

		Doc.НалогообложениеНДС = УправлениеНебольшойФирмойСервер.НалогообложениеНДС(Doc.Организация,, Doc.Date);

		Txt = StrTemplate("Нове замовлення: %1 %2; менеджер=%3; клієнт=%4; сума=%5",
				Order["number"], Order["date"], Manager, Order["client_description"], Order["price"]);

		WriteLogEvent("AgentVenta", EventLogLevel.Note,, Doc.Ref, Txt);

	EndIf;

	///////////////////////////////////////////// отказ загрузки
	If Doc.Posted Then

		Message("Document skipped (posted): "+Doc.Ref);
		Return True;

	EndIf;

	///////////////////////////////////////////// комментарий
	If NOT IsBlankString(Order["notes"]) Then
		Doc.Комментарий = Order["notes"];
	EndIf;

	///////////////////////////////////////////// контрагент
	Doc.Контрагент = Catalogs.Контрагенты.GetRef(New UUID(Order["client_guid"]));

	///////////////////////////////////////////// подразделение контрагента
	Doc.ПодразделениеКонтрагента = Doc.Контрагент.ПодразделениеКонтрагентаПоУмолчанию;

	///////////////////////////////////////////// первая форма
	Doc.Ф_ПерваяФорма = Doc.ПодразделениеКонтрагента.НакладнаяПервойФормы;
	Doc.Ф_ОформитьОтФОП = Doc.Ф_ПерваяФорма;

	If Doc.Ф_ПерваяФорма Then

		ЗначениеНастройки = УправлениеНебольшойФирмойПовтИсп.ПолучитьЗначениеПоУмолчаниюПользователя(Doc.Ответственный, "ОсновнаяОрганизация");
		Если ЗначениеЗаполнено(ЗначениеНастройки) Тогда
			Doc.Ф_ОрганизацияФОП = ЗначениеНастройки;
		Иначе
			Doc.Ф_ОрганизацияФОП = Справочники.Организации.ОсновнаяОрганизация;
		КонецЕсли;

	EndIf;

	///////////////////////////////////////////// договор контрагента
	МенеджерСправочника = Справочники.ДоговорыКонтрагентов;

	СписокВидовДоговоров = МенеджерСправочника.ПолучитьСписокВидовДоговораДляДокумента(Doc.Ref, Doc.ВидОперации);
	Doc.Договор = МенеджерСправочника.ПолучитьДоговорПоУмолчаниюПоОрганизацииВидуДоговора(Doc.Контрагент, Doc.Организация, СписокВидовДоговоров);

	///////////////////////////////////////////// валюта и тип цен -- по договору
	Если ЗначениеЗаполнено(Doc.Договор) Тогда
		Doc.ВалютаДокумента = Doc.Договор.ВалютаРасчетов;
		ВалютаРасчетовКурсКратность = РегистрыСведений.КурсыВалют.ПолучитьПоследнее(Doc.Дата, Новый Структура("Валюта", Doc.Договор.ВалютаРасчетов));
		Doc.Курс      = ?(ВалютаРасчетовКурсКратность.Курс = 0, 1, ВалютаРасчетовКурсКратность.Курс);
		Doc.Кратность = ?(ВалютаРасчетовКурсКратность.Кратность = 0, 1, ВалютаРасчетовКурсКратность.Кратность);
		Doc.ВидСкидкиНаценки = Doc.Договор.ВидСкидкиНаценки;
		Doc.ВидЦен = Doc.Договор.ВидЦен;
	КонецЕсли;

	///////////////////////////////////////////// доп. поля заказа из приложения (п.15)

	// тип цен из приложения имеет приоритет над договорным
	ВидЦенЗаказа = ResolveCatalogRef(Справочники.ВидыЦен, Order["price_type"]);
	Если ВидЦенЗаказа <> Неопределено Тогда
		Doc.ВидЦен = ВидЦенЗаказа;
	КонецЕсли;

	// склад / структурная единица продажи
	СкладЗаказа = ResolveCatalogRef(Справочники.СтруктурныеЕдиницы, Order["store_guid"]);
	Если СкладЗаказа <> Неопределено Тогда
		Doc.СтруктурнаяЕдиницаПродажи = СкладЗаказа;
	КонецЕсли;

	// скидка по документу (процент)
	Скидка = Order["discount"];
	Если ТипЗнч(Скидка) = Тип("Число") Тогда
		Doc.ПроцентСкидки = Скидка;
	КонецЕсли;

	// тип денежных средств: CASH -> Наличные; CREDIT/прочее -> значение по умолчанию
	Если Order["payment_type"] = "CASH" Тогда
		Doc.ТипДенежныхСредств = Перечисления.ТипыДенежныхСредств.Наличные;
	КонецЕсли;

	// возврат: у ЗаказПокупателя нет вида операции "возврат" — помечаем комментарием
	Если Order["is_return"] = 1 Тогда
		Метка = "[ВОЗВРАТ]";
		Если Найти(Doc.Комментарий, Метка) = 0 Тогда
			Doc.Комментарий = СокрЛП(Метка + " " + Doc.Комментарий);
		КонецЕсли;
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,, Doc.Ref,
			StrTemplate("Замовлення %1 позначене як повернення (is_return=1) — у ЗаказПокупателя немає виду операції ""повернення""", Order["number"]));
	КонецЕсли;

	///////////////////////////////////////////// товары
	Doc.Запасы.Clear();

	For Each Item In Order["items"] Do

		Row = Doc.Запасы.Add();

		Row.Номенклатура = Catalogs.Номенклатура.GetRef(New UUID(Item["item_guid"]));

		// копия логики из формы документа при изменении товара
		ТоварыНоменклатураПриИзменении(Row, Doc);

		Row.Артикул 		= Row.Номенклатура.Артикул;
		Row.Тон 			= Row.Номенклатура.Тон;

		Row.ЦенаБезСкидки 	= Item["price"];
		Row.Цена 			= Item["price"];
		Row.Количество 		= Item["quantity"];
		Row.Сумма			= Item["sum"];

		If Row.Количество = 0 OR Row.ЦенаБезСкидки = 0 Then
			Continue;
		EndIf;

		Total = Row.ЦенаБезСкидки * Row.Количество;

		Row.Цена = Row.Сумма / Row.Количество;
		Row.ПроцентСкидкиНаценки = Item["sum_discount"] * 100 / Total;

		РассчитатьСуммуВСтрокеТабличнойЧасти(Row, Doc);

	EndDo;

	Try

		Doc.Write(DocumentWriteMode.Write);
		//Message("Order saved: "+Doc.Ref);

	Except
		Txt = StrTemplate("%1: write error: %2", Doc.Ref, BriefErrorDescription(ErrorInfo()));
		Message(Txt);
		WriteLogEvent("AgentVenta", EventLogLevel.Error,, Doc.Ref, Txt);
		Return False;
	EndTry;

	Try

		Doc.Write(DocumentWriteMode.Posting);
		Message("Order posted: "+Doc.Ref);

	Except
		Txt = StrTemplate("%1: post error: %2", Doc.Ref, BriefErrorDescription(ErrorInfo()));
		Message(Txt);
		WriteLogEvent("AgentVenta", EventLogLevel.Error,, Doc.Ref, Txt);
		Return False;
	EndTry;

	Return True;

EndFunction

// Загружает оплату из очереди pull в документ ПоступлениеВКассу.
// Возвращает Истина, если документ можно подтвердить бекенду (pull/ack):
// успешно записан/проведён, уже проведён ранее либо осознанно отброшен
// (некорректный guid, нулевая сумма). Возвращает Ложь для исправимых сбоев
// (неизвестное устройство, ошибка записи/проведения) — документ будет выдан повторно.
Function DownloadCash(Cash, Params)

	// дата документа указана в данных в виде timestamp в секундах
	// (та же база времени, что и в заказе)
	BaseDate = Date(1970, 1, 1, 0, 0, 0);

	Manager = GetManager(Cash["userID"]);

	If Manager = Undefined Then

		Txt = "Невідомий пристрій, оплата пропущена; ID=" + Cash["userID"];

		WriteLogEvent("AgentVenta", EventLogLevel.Error,,, Txt);
		Message(Txt);
		Return False;

	EndIf;

	///////////////////////////////////////////// ссылка на документ
	UID = Cash["guid"];

	Try

		Ref = Documents.ПоступлениеВКассу.GetRef(New UUID(UID));

	Except
		Message(StrTemplate("id=%1; %2", UID, BriefErrorDescription(ErrorInfo())));
		Return True;
	EndTry;

	Doc = Ref.GetObject();

	IsNew = (Doc = Undefined);

	///////////////////////////////////////////// создание нового документа
	If IsNew Then

		Doc = Documents.ПоступлениеВКассу.CreateDocument();
		Doc.SetNewObjectRef(Ref);

	EndIf;

	///////////////////////////////////////////// отказ загрузки
	If Doc.Posted Then

		Message("Cash skipped (posted): "+Doc.Ref);
		Return True;

	EndIf;

	Sum = Number(Cash["sum"]);

	If Sum = 0 Then

		Message("Cash skipped (zero sum): "+Doc.Ref);
		Return True;

	EndIf;

	///////////////////////////////////////////// заполнение по заказу или как аванс
	// reference_guid - ссылка на оплачиваемый заказ (создан ранее из "order").
	// Если заказ найден - используем штатное заполнение по основанию
	// (сам заполнит договор, ставку НДС, курс и "Расшифровку платежа"),
	// иначе оформляем оплату как аванс от покупателя.
	Doc.РасшифровкаПлатежа.Clear();

	FilledFromOrder = False;
	RefGuid = Cash["reference_guid"];

	If NOT IsBlankString(RefGuid) Then

		Try
			OrderRef = Documents.ЗаказПокупателя.GetRef(New UUID(RefGuid));
		Except
			OrderRef = Undefined;
		EndTry;

		If OrderRef <> Undefined Then

			Doc.Заполнить(New Structure("Документ, Сумма", OrderRef, Sum));
			FilledFromOrder = (Doc.РасшифровкаПлатежа.Count() > 0);

		EndIf;

	EndIf;

	If NOT FilledFromOrder Then

		FillCashAdvance(Doc, Cash["client_guid"], Cash["company_guid"], Sum, Manager);

	EndIf;

	///////////////////////////////////////////// шапка
	DocDate = BaseDate + ?(Cash["time"] = Undefined, 0, Number(Cash["time"]));
	Doc.Date = ?(ЗначениеЗаполнено(DocDate), DocDate, ТекущаяДата());

	Doc.Ответственный = Manager;

	If IsNew Then
		Doc.Автор = Manager;
	EndIf;

	If NOT IsBlankString(Cash["notes"]) Then
		Doc.Комментарий = Cash["notes"];
	EndIf;

	If NOT ЗначениеЗаполнено(Doc.Статья) Then
		Doc.Статья = Справочники.СтатьиДвиженияДенежныхСредств.ОплатаОтПокупателей;
	EndIf;

	If NOT ЗначениеЗаполнено(Doc.ПринятоОт) AND ЗначениеЗаполнено(Doc.Контрагент) Then
		Doc.ПринятоОт = ?(Doc.Контрагент.НаименованиеПолное = "", Doc.Контрагент.Наименование, Doc.Контрагент.НаименованиеПолное);
	EndIf;

	Doc.СуммаДокумента = Doc.РасшифровкаПлатежа.Итог("СуммаПлатежа");

	If IsNew Then

		Txt = StrTemplate("Нова оплата: %1 %2; менеджер=%3; клієнт=%4; сума=%5",
				Cash["number"], Cash["date"], Manager, Cash["client"], Sum);

		WriteLogEvent("AgentVenta", EventLogLevel.Note,, Doc.Ref, Txt);

	EndIf;

	///////////////////////////////////////////// запись
	Try

		Doc.Write(DocumentWriteMode.Write);

	Except
		Txt = StrTemplate("%1: write error: %2", Doc.Ref, BriefErrorDescription(ErrorInfo()));
		Message(Txt);
		WriteLogEvent("AgentVenta", EventLogLevel.Error,, Doc.Ref, Txt);
		Return False;
	EndTry;

	Try

		Doc.Write(DocumentWriteMode.Posting);
		Message("Cash posted: "+Doc.Ref);

	Except
		Txt = StrTemplate("%1: post error: %2", Doc.Ref, BriefErrorDescription(ErrorInfo()));
		Message(Txt);
		WriteLogEvent("AgentVenta", EventLogLevel.Error,, Doc.Ref, Txt);
		Return False;
	EndTry;

	Return True;

EndFunction

// Заполняет "Поступление в кассу" как аванс от покупателя, когда оплата
// не привязана к заказу (reference_guid пуст или заказ ещё не загружен).
Procedure FillCashAdvance(Doc, ClientGuid, CompanyGuid, Sum, Manager)

	Doc.ВидОперации = Перечисления.ВидыОперацийПоступлениеВКассу.ОтПокупателя;

	// организация: по company_guid, иначе значение по умолчанию из ЗаполнитьШапкуДокумента
	Организация = Undefined;
	If NOT IsBlankString(CompanyGuid) Then
		Try
			Организация = Справочники.Организации.GetRef(New UUID(CompanyGuid));
		Except
			Организация = Undefined;
		EndTry;
	EndIf;

	УправлениеНебольшойФирмойСервер.ЗаполнитьШапкуДокумента(Doc, Doc.ВидОперации, ,,,,,);

	If Организация <> Undefined Then
		Doc.Организация = Организация;
	EndIf;

	Doc.Контрагент = Справочники.Контрагенты.GetRef(New UUID(ClientGuid));

	///////////////////////////////////////////// касса
	НайденнаяКасса = Справочники.Кассы.НайтиПоРеквизиту("Ответственный", Manager);
	If НайденнаяКасса.Пустая() Then
		Doc.Касса = Doc.Организация.КассаПоУмолчанию;
	Else
		Doc.Касса = НайденнаяКасса;
	EndIf;

	Doc.ВалютаДенежныхСредств = ?(ЗначениеЗаполнено(Doc.Касса.ВалютаПоУмолчанию), Doc.Касса.ВалютаПоУмолчанию, Константы.НациональнаяВалюта.Получить());
	Doc.НалогообложениеНДС = УправлениеНебольшойФирмойСервер.НалогообложениеНДС(Doc.Организация,, ?(ЗначениеЗаполнено(Doc.Дата), Doc.Дата, ТекущаяДата()));

	///////////////////////////////////////////// договор
	If Doc.Контрагент.ВестиРасчетыПоДоговорам Then
		Doc.Договор = Doc.Контрагент.ДоговорПоУмолчанию;
	EndIf;

	Doc.СуммаДокумента = Sum;

	///////////////////////////////////////////// расшифровка платежа (аванс)
	Стр = Doc.РасшифровкаПлатежа.Добавить();
	Стр.Договор       = Doc.Договор;
	Стр.ПризнакАванса = True;
	Стр.СуммаПлатежа  = Sum;
	Стр.СуммаРасчетов = Sum;
	Стр.Курс          = 1;
	Стр.Кратность     = 1;
	Стр.Заказ         = Документы.ЗаказПокупателя.ПустаяСсылка();

	If Doc.НалогообложениеНДС = Перечисления.ТипыНалогообложенияНДС.ОблагаетсяНДС Then
		Стр.СтавкаНДС = ПодборНоменклатурыВДокументахПовтИсп.ПолучитьСтавкуНДСОрганизации(Doc.Организация);
		Стр.СуммаНДС  = ?(ЗначениеЗаполнено(Стр.СтавкаНДС) AND Стр.СтавкаНДС.Ставка > 0, Sum * (1 - 1 / ((Стр.СтавкаНДС.Ставка + 100) / 100)), 0);
	Else
		Стр.СтавкаНДС = УправлениеНебольшойФирмойПовтИсп.ПолучитьСтавкуНДСБезНДС();
		Стр.СуммаНДС  = 0;
	EndIf;

EndProcedure

// Возвращает ссылку на существующий элемент справочника по строковому GUID,
// либо Неопределено (пустой/некорректный GUID или элемент не найден в базе).
Function ResolveCatalogRef(Manager, GuidStr)

	If IsBlankString(GuidStr) Then
		Return Undefined;
	EndIf;

	Try
		Ref = Manager.GetRef(New UUID(GuidStr));
	Except
		Return Undefined;
	EndTry;

	If Ref.GetObject() = Undefined Then
		Return Undefined;
	EndIf;

	Return Ref;

EndFunction // ResolveCatalogRef()

Function GetManager(ID)

	Query = New Query;
	Query.Text =
	"SELECT TOP 1
	|	AV_SettingsDevices.Manager AS Ref
	|FROM
	|	Catalog.AV_Settings.Devices AS AV_SettingsDevices
	|WHERE
	|	AV_SettingsDevices.ID = &ID
	|	AND AV_SettingsDevices.Enabled
	|	AND NOT AV_SettingsDevices.Ref.DeletionMark";

	Query.SetParameter("ID", ID);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		Return Sel.Ref;

	EndDo;

	Return Undefined;

EndFunction // GetManager()

#EndRegion

#Region OrderFormHelpers

Процедура РассчитатьСуммуНДС(СтрокаТабличнойЧасти, Объект)

	СтавкаНДС = УправлениеНебольшойФирмойПовтИсп.ПолучитьЗначениеСтавкиНДС(СтрокаТабличнойЧасти.СтавкаНДС);

	СтрокаТабличнойЧасти.СуммаНДС = ?(Объект.СуммаВключаетНДС,
									  СтрокаТабличнойЧасти.Сумма - (СтрокаТабличнойЧасти.Сумма) / ((СтавкаНДС + 100) / 100),
									  СтрокаТабличнойЧасти.Сумма * СтавкаНДС / 100);

КонецПроцедуры // ПересчитатьСуммыДокумента()

Функция ПолучитьДанныеНоменклатураПриИзменении(СтруктураДанные)

	СтруктураДанные.Вставить("ЕдиницаИзмерения", СтруктураДанные.Номенклатура.ЕдиницаИзмерения);
	СтруктураДанные.Вставить("ЭтоУслуга", СтруктураДанные.Номенклатура.ТипНоменклатуры = Перечисления.ТипыНоменклатуры.Услуга);
	СтруктураДанные.Вставить("ЭтоЗапас", СтруктураДанные.Номенклатура.ТипНоменклатуры = Перечисления.ТипыНоменклатуры.Запас);
	СтруктураДанные.Вставить("Артикул", СтруктураДанные.Номенклатура.Артикул);
	СтруктураДанные.Вставить("Тон", СтруктураДанные.Номенклатура.Тон);

	Если СтруктураДанные.Свойство("НормаВремени") Тогда
		СтруктураДанные.НормаВремени = УправлениеНебольшойФирмойСервер.ПолучитьНормуВремениРаботы(СтруктураДанные);
	КонецЕсли;

	Если СтруктураДанные.Свойство("НалогообложениеНДС")
		И НЕ СтруктураДанные.НалогообложениеНДС = Перечисления.ТипыНалогообложенияНДС.ОблагаетсяНДС Тогда

		Если СтруктураДанные.НалогообложениеНДС = Перечисления.ТипыНалогообложенияНДС.НеОблагаетсяНДС Тогда
			СтруктураДанные.Вставить("СтавкаНДС", УправлениеНебольшойФирмойПовтИсп.ПолучитьСтавкуНДСБезНДС());
		Иначе
			СтруктураДанные.Вставить("СтавкаНДС", УправлениеНебольшойФирмойПовтИсп.ПолучитьСтавкуНДСНоль());
		КонецЕсли;

	ИначеЕсли ЗначениеЗаполнено(СтруктураДанные.Номенклатура.СтавкаНДС) Тогда
		СтруктураДанные.Вставить("СтавкаНДС", СтруктураДанные.Номенклатура.СтавкаНДС);
	Иначе
		СтруктураДанные.Вставить("СтавкаНДС", СтруктураДанные.Организация.СтавкаНДСПоУмолчанию);
	КонецЕсли;

	Если СтруктураДанные.Свойство("Характеристика") Тогда
		СтруктураДанные.Вставить("Спецификация", УправлениеНебольшойФирмойСервер.ПолучитьПоУмолчаниюСпецификацию(СтруктураДанные.Номенклатура, СтруктураДанные.Характеристика));
	Иначе
		СтруктураДанные.Вставить("Спецификация", УправлениеНебольшойФирмойСервер.ПолучитьПоУмолчаниюСпецификацию(СтруктураДанные.Номенклатура));
	КонецЕсли;

	Если СтруктураДанные.Свойство("ВидЦен") Тогда

		Если НЕ СтруктураДанные.Свойство("Характеристика") Тогда
			СтруктураДанные.Вставить("Характеристика", Справочники.ХарактеристикиНоменклатуры.ПустаяСсылка());
		КонецЕсли;

		Если СтруктураДанные.Свойство("ВидРабот") Тогда

			Если СтруктураДанные.Номенклатура.ФиксированнаяСтоимость Тогда

				Цена = УправлениеНебольшойФирмойСервер.ПолучитьЦенуНоменклатурыПоВидуЦен(СтруктураДанные);
				СтруктураДанные.Вставить("Цена", Цена);

			Иначе

				СтруктураДанные.Номенклатура = СтруктураДанные.ВидРабот;
				СтруктураДанные.Характеристика = Справочники.ХарактеристикиНоменклатуры.ПустаяСсылка();
				Цена = УправлениеНебольшойФирмойСервер.ПолучитьЦенуНоменклатурыПоВидуЦен(СтруктураДанные);
				СтруктураДанные.Вставить("Цена", Цена);

			КонецЕсли;

		Иначе

			Цена = УправлениеНебольшойФирмойСервер.ПолучитьЦенуНоменклатурыПоВидуЦен(СтруктураДанные);
			СтруктураДанные.Вставить("Цена", Цена);

		КонецЕсли;

	Иначе

		СтруктураДанные.Вставить("Цена", 0);

	КонецЕсли;

	Если СтруктураДанные.Свойство("ВидСкидкиНаценки")
		И ЗначениеЗаполнено(СтруктураДанные.ВидСкидкиНаценки) Тогда
		СтруктураДанные.Вставить("ПроцентСкидкиНаценки", СтруктураДанные.ВидСкидкиНаценки.Процент);
	Иначе
		СтруктураДанные.Вставить("ПроцентСкидкиНаценки", 0);
	КонецЕсли;

	Возврат СтруктураДанные;

КонецФункции // ПолучитьДанныеНоменклатураПриИзменении()

Процедура РассчитатьСуммуВСтрокеТабличнойЧасти(СтрокаТабличнойЧасти, Объект)

	// Сумма.
	//АС_СброситьРассчетАвтоматическихСкидок(СтрокаТабличнойЧасти);

	СтрокаТабличнойЧасти.Сумма = СтрокаТабличнойЧасти.Количество * СтрокаТабличнойЧасти.Цена;
	СтрокаТабличнойЧасти.СуммаБезСкидки = СтрокаТабличнойЧасти.Количество * СтрокаТабличнойЧасти.ЦенаБезСкидки;
	СтрокаТабличнойЧасти.СуммаСкидки = СтрокаТабличнойЧасти.СуммаБезСкидки - СтрокаТабличнойЧасти.Сумма;

	// Сумма НДС.
	РассчитатьСуммуНДС(СтрокаТабличнойЧасти, Объект);

	// Всего.
	СтрокаТабличнойЧасти.Всего = СтрокаТабличнойЧасти.Сумма + ?(Объект.СуммаВключаетНДС, 0, СтрокаТабличнойЧасти.СуммаНДС);

КонецПроцедуры // РассчитатьСуммуВСтрокеТабличнойЧасти()

Процедура ТоварыНоменклатураПриИзменении(СтрокаТабличнойЧасти, Объект)

	СтруктураДанные = Новый Структура;
	СтруктураДанные.Вставить("Организация", Объект.Организация);
	СтруктураДанные.Вставить("Номенклатура", СтрокаТабличнойЧасти.Номенклатура);
	СтруктураДанные.Вставить("Характеристика", СтрокаТабличнойЧасти.Характеристика);
	СтруктураДанные.Вставить("НалогообложениеНДС", Объект.НалогообложениеНДС);

	Если ЗначениеЗаполнено(Объект.ВидЦен) Тогда

		СтруктураДанные.Вставить("ДатаОбработки", Объект.Дата);
		СтруктураДанные.Вставить("ВалютаДокумента", Объект.ВалютаДокумента);
		СтруктураДанные.Вставить("СуммаВключаетНДС", Объект.СуммаВключаетНДС);
		СтруктураДанные.Вставить("ВидЦен", Объект.ВидЦен);
		СтруктураДанные.Вставить("Коэффициент", 1);
		СтруктураДанные.Вставить("ВидСкидкиНаценки", Объект.ВидСкидкиНаценки);

	КонецЕсли;

	СтруктураДанные = ПолучитьДанныеНоменклатураПриИзменении(СтруктураДанные);

	СтрокаТабличнойЧасти.ЕдиницаИзмерения = СтруктураДанные.ЕдиницаИзмерения;
	СтрокаТабличнойЧасти.Спецификация = СтруктураДанные.Спецификация;
	СтрокаТабличнойЧасти.Количество = 1;
	СтрокаТабличнойЧасти.Цена = СтруктураДанные.Цена;
	СтрокаТабличнойЧасти.ПроцентСкидкиНаценки = СтруктураДанные.ПроцентСкидкиНаценки;
	СтрокаТабличнойЧасти.СтавкаНДС = СтруктураДанные.СтавкаНДС;
	СтрокаТабличнойЧасти.Содержание = "";

	СтрокаТабличнойЧасти.ТипНоменклатурыЗапас = СтруктураДанные.ЭтоЗапас;

	РассчитатьСуммуВСтрокеТабличнойЧасти(СтрокаТабличнойЧасти, Объект);

КонецПроцедуры // ТоварыНоменклатураПриИзменении()

#EndRegion

#Region API_Calls

// Возвращает соединение с сервером обмена.
// Адрес берется из Params.Server (реквизит Server справочника AV_Settings);
// если не заполнен, используется адрес по умолчанию.
Function ServerConnection(Params)

	Server = "";

	If Params.Property("Server") Then
		Server = TrimAll(Params.Server);
	EndIf;

	If IsBlankString(Server) Then
		Server = "https://lic.nomadus.net";
	EndIf;

	Return HTTP_NewConnection(Server, , True);

EndFunction // ServerConnection()

Procedure DoRequest(Params, Path, Data)
	Var Err, StatusCode;

	If NOT Params.Property("CC") Then
		Params.Insert("CC", 0);
	EndIf;

	// отладка управляется реквизитами Debug и DebugPath справочника AV_Settings
	Debug 		= False;
	LogPath 	= "";

	If Params.Property("Debug") Then
		Debug = Params.Debug = True;
	EndIf;

	If Params.Property("DebugPath") Then
		LogPath = TrimAll(Params.DebugPath);
	EndIf;

	If IsBlankString(LogPath) Then
		Debug = False;
	EndIf;

	CC 			= Params.CC + 1;
	Params.CC 	= CC;

	// добавление метки времени в каждый элемент данных
	Items = Undefined;
	If TypeOf(Data) = Type("Structure") AND Data.Property("data", Items) Then

		For Each Item In Items Do
			Item.Insert("timestamp", Params.Timestamp);
		EndDo;

	EndIf;

	Req = HTTP_RequestWithJSON(Data, Err);

	If Err<>Undefined Then

		Params.Err = "HTTP request prepare: " + Err;
		Return;

	EndIf;

	If Params.Conn = Undefined Then

		Params.Conn = ServerConnection(Params);

	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = StrTemplate("/api/v1/%1", Path);

	If Debug Then

		Sep = ?(Right(LogPath, 1) = "\" OR Right(LogPath, 1) = "/", "", "/");
		FileName = StrTemplate("%1%2request_%3.txt", LogPath, Sep, CC);

		// ошибка записи отладки не должна прерывать обмен
		Try
			Txt = New TextDocument;
			Txt.SetText(Req.GetBodyAsString());
			Txt.Write(FileName);
		Except
			WriteLogEvent("AgentVenta", EventLogLevel.Warning,,,
				StrTemplate("Debug write failed: %1; path=%2", BriefErrorDescription(ErrorInfo()), FileName));
		EndTry;

	EndIf;

	// Повторы при HTTP 429 (Too Many Requests).
	RetryDelays = New Array;
	RetryDelays.Add(20);
	RetryDelays.Add(30);
	RetryDelays.Add(60);

	AttemptIndex = 0;
	Response     = "";

	While True Do

		Response = HTTP_POST(Params.Conn, , Req, StatusCode, Err);

		If Err<>Undefined Then

			Params.Err = StrTemplate("HTTP response: %1; status=%2", Err, StatusCode);
			Return;

		EndIf;

		If StatusCode = 429 AND AttemptIndex < RetryDelays.Count() Then

			Delay = RetryDelays[AttemptIndex];
			AttemptIndex = AttemptIndex + 1;

			WriteLogEvent(
				"AgentVenta",
				EventLogLevel.Warning, , ,
				StrTemplate("HTTP 429 (rate limit); path=%1; attempt %2/%3; sleeping %4s",
					Path, AttemptIndex, RetryDelays.Count(), Delay));

			Sleep(Delay);
			Continue;

		EndIf;

		Break;

	EndDo;

	Resp = JSON_Unmarshall(Response, Err);

	If Err<>Undefined Then

		Params.Err = StrTemplate("HTTP response: %1; %2; status=%3", Resp, Err, StatusCode);
		Return;

	EndIf;

	If StatusCode<>200 Then

		Params.Err = StrTemplate("HTTP response: %1 %2 %3", StatusCode, Resp["status_message"], Resp["error"]);
		Return;

	EndIf;

	Params.Response = Resp;

EndProcedure

// Загружает двоичные данные картинки на relay-сервер: PUT /api/v1/images/{guid}.
// Контракт бекенда (tasks/images-via-relay-plan.md, реализовано):
//  - тело: сырые байты, Content-Type по расширению файла (иначе 415);
//  - заголовок X-Image-Time: версия картинки (мс), необязателен;
//  - лимит 2 МБ (413); отдельный rate-limit 600 req/min;
//  - успешный ответ 200/201, стандартный конверт: {"success":true,"data":{"stored":true,"url":"..."}};
//    data.url — готовый абсолютный URL с ?v={time}, используется как есть.
// Возвращает URL или Неопределено (проблема конкретного файла — обмен продолжается).
// Транспортный обрыв (напр. "Transferred a partial file") ретраится и при
// исчерпании попыток приводит к пропуску файла (без Params.Err), чтобы одна
// флаки-картинка не валила весь diff и не зацикливала повторную отправку.
// Params.Err ставится только при исчерпании повторов 429 (жёсткий rate-limit).
Function UploadImage(Params, FileRef, Extension, VersionMs)
	Var Err;

	Try
		BinData = ПрисоединенныеФайлы.ПолучитьДвоичныеДанныеФайла(FileRef);
	Except
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,, FileRef,
			StrTemplate("Image read failed: %1", BriefErrorDescription(ErrorInfo())));
		Return Undefined;
	EndTry;

	Ext = Lower(TrimAll(Extension));

	If Ext = "jpg" OR Ext = "jpeg" Then
		ContentType = "image/jpeg";
	ElsIf Ext = "png" Then
		ContentType = "image/png";
	ElsIf Ext = "webp" Then
		ContentType = "image/webp";
	Else
		Return Undefined;
	EndIf;

	If Params.Conn = Undefined Then
		Params.Conn = ServerConnection(Params);
	EndIf;

	Req = New HTTPRequest;
	Req.Headers.Insert("Content-Type", ContentType);
	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.Headers.Insert("X-Image-Time", Format(VersionMs, "NG=0"));
	Req.ResourceAddress = StrTemplate("/api/v1/images/%1", String(FileRef.UUID()));
	Req.SetBodyFromBinaryData(BinData);

	// Повторы при HTTP 429 (Too Many Requests).
	RetryDelays = New Array;
	RetryDelays.Add(20);
	RetryDelays.Add(30);
	RetryDelays.Add(60);

	AttemptIndex = 0;
	StatusCode   = 0;

	While True Do

		Try
			Resp = Params.Conn.Put(Req);
		Except
			// транспортный обрыв (напр. "Transferred a partial file") — повторяем,
			// как и 429; при исчерпании попыток пропускаем файл (Return Undefined
			// без Params.Err), чтобы одна флаки-картинка не валила весь diff и не
			// зацикливала повторную отправку всего пакета изменений.
			ErrText = BriefErrorDescription(ErrorInfo());
			If AttemptIndex < RetryDelays.Count() Then
				Delay = RetryDelays[AttemptIndex];
				AttemptIndex = AttemptIndex + 1;
				WriteLogEvent("AgentVenta", EventLogLevel.Warning,, FileRef,
					StrTemplate("Image upload transport error; attempt %1/%2; sleeping %3s; %4",
						AttemptIndex, RetryDelays.Count(), Delay, ErrText));
				Sleep(Delay);
				Continue;
			EndIf;
			WriteLogEvent("AgentVenta", EventLogLevel.Warning,, FileRef,
				StrTemplate("Image upload transport error, file skipped: %1", ErrText));
			Return Undefined;
		EndTry;

		StatusCode = Resp.StatusCode;

		If StatusCode = 429 AND AttemptIndex < RetryDelays.Count() Then

			Delay = RetryDelays[AttemptIndex];
			AttemptIndex = AttemptIndex + 1;

			WriteLogEvent(
				"AgentVenta",
				EventLogLevel.Warning, , ,
				StrTemplate("HTTP 429 (rate limit); image upload; attempt %1/%2; sleeping %3s",
					AttemptIndex, RetryDelays.Count(), Delay));

			Sleep(Delay);
			Continue;

		EndIf;

		Break;

	EndDo;

	If StatusCode = 429 Then
		Params.Err = "Image upload: rate limited (429)";
		Return Undefined;
	EndIf;

	If StatusCode <> 200 AND StatusCode <> 201 Then
		// проблема конкретного файла (размер, формат) — пропускаем, обмен продолжается
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,, FileRef,
			StrTemplate("Image upload rejected: status=%1; %2", StatusCode, Resp.GetBodyAsString()));
		Return Undefined;
	EndIf;

	Body = JSON_Unmarshall(Resp.GetBodyAsString(), Err);

	// полезная нагрузка в стандартном конверте ответа: data.url, уже с ?v={time}
	Payload = ?(Body = Undefined, Undefined, Body["data"]);
	Url 	= ?(Payload = Undefined, Undefined, Payload["url"]);

	If NOT ValueIsFilled(Url) Then
		WriteLogEvent("AgentVenta", EventLogLevel.Warning,, FileRef,
			StrTemplate("Image upload: no data.url in response; %1", Err));
		Return Undefined;
	EndIf;

	Return Url;

EndFunction // UploadImage()

Procedure GET(Params, Path)
	Var Err, StatusCode;

	Params.Insert("Response", Undefined);

	Req = New HTTPRequest();

	If Params.Conn = Undefined Then

		Params.Conn = ServerConnection(Params);

	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = StrTemplate("/api/v1/%1", Path);

	// Повторы при HTTP 429 (Too Many Requests).
	RetryDelays = New Array;
	RetryDelays.Add(20);
	RetryDelays.Add(30);
	RetryDelays.Add(60);

	AttemptIndex = 0;
	Response     = "";

	While True Do

		Response = HTTP_GET(Params.Conn, , Req, StatusCode, Err);

		If Err<>Undefined Then

			Params.Err = "HTTP request: " + Err;
			Return;

		EndIf;

		If StatusCode = 429 AND AttemptIndex < RetryDelays.Count() Then

			Delay = RetryDelays[AttemptIndex];
			AttemptIndex = AttemptIndex + 1;

			WriteLogEvent("AgentVenta", EventLogLevel.Warning, , ,
				StrTemplate("HTTP 429 (rate limit); path=%1; attempt %2/%3; sleeping %4s",
					Path, AttemptIndex, RetryDelays.Count(), Delay));

			Sleep(Delay);
			Continue;

		EndIf;

		Break;

	EndDo;

	Resp = JSON_Unmarshall(Response, Err);

	If Err<>Undefined Then

		Params.Err = "HTTP response: " + Resp + "; " + Err;
		Return;

	EndIf;

	If StatusCode<>200 Then

		Params.Err = StrTemplate("HTTP response: %1 %2 %3", StatusCode, Resp["status_message"], Resp["error"]);
		Return;

	EndIf;

	Params.Response = Resp;

EndProcedure

Procedure DELETE(Params, Path)
	Var Err, StatusCode;

	Req = New HTTPRequest();

	If Params.Conn = Undefined Then

		Params.Conn = ServerConnection(Params);

	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = StrTemplate("/api/v1/%1", Path);

	// Повторы при HTTP 429 (Too Many Requests).
	RetryDelays = New Array;
	RetryDelays.Add(20);
	RetryDelays.Add(30);
	RetryDelays.Add(60);

	AttemptIndex = 0;
	Response     = "";

	While True Do

		Response = HTTP_DELETE(Params.Conn, , Req, StatusCode, Err);

		If Err<>Undefined Then

			Params.Err = "HTTP request: " + Err;
			Return;

		EndIf;

		If StatusCode = 429 AND AttemptIndex < RetryDelays.Count() Then

			Delay = RetryDelays[AttemptIndex];
			AttemptIndex = AttemptIndex + 1;

			WriteLogEvent("AgentVenta", EventLogLevel.Warning, , ,
				StrTemplate("HTTP 429 (rate limit); path=%1; attempt %2/%3; sleeping %4s",
					Path, AttemptIndex, RetryDelays.Count(), Delay));

			Sleep(Delay);
			Continue;

		EndIf;

		Break;

	EndDo;

	Resp = JSON_Unmarshall(Response, Err);

	If Err<>Undefined Then

		Resp = Undefined;

	EndIf;

	If StatusCode<>200 Then

		Params.Err = StrTemplate("HTTP response: %1 %2 %3", StatusCode, Resp["status_message"], Resp["error"]);
		Return;

	EndIf;

	Params.Response = Resp;

EndProcedure

Procedure Sleep(Seconds) Export

	Session = GetCurrentInfoBaseSession();
	Job = Session.GetBackgroundJob();

	If Job = Undefined Then
		Params = New Array;
		Params.Add(Seconds);
		Job = BackgroundJobs.Execute("AV_Common.Sleep", Params);
	EndIf;

	Try
		Job.WaitForCompletion(Seconds);
	Except
	    Return;
	EndTry;

EndProcedure

#EndRegion

#Region OtherMethods

Procedure ReadDevices(Token, Response, Server="") Export

	Params = New Structure;
	Params.Insert("Conn", 		Undefined);
	Params.Insert("Token", 		Token);
	Params.Insert("Server",		Server);
	Params.Insert("Err",		Undefined);
	Params.Insert("Response",	Undefined);

	GET(Params, "devices");

	If Params.Err<>Undefined Then
		Message(Params.Err);
	EndIf;

	Response = Params.Response;

EndProcedure

Procedure ApproveDevice(Token, ID, Err, Server="") Export

	Params = New Structure;
	Params.Insert("Conn", 		Undefined);
	Params.Insert("Token", 		Token);
	Params.Insert("Server",		Server);
	Params.Insert("Err",		Undefined);
	Params.Insert("Response",	Undefined);

	Data = New Structure;
	Data.Insert("device_uuid", ID);

	DoRequest(Params, "devices/register", Data);

	Err = Params.Err;

EndProcedure

Procedure RemoveDevice(Token, ID, Err, Server="") Export

	Params = New Structure;
	Params.Insert("Conn", 		Undefined);
	Params.Insert("Token", 		Token);
	Params.Insert("Server",		Server);
	Params.Insert("Err",		Undefined);
	Params.Insert("Response",	Undefined);

	DELETE(Params, StrTemplate("devices/%1", ID));

	Err = Params.Err;

EndProcedure

#EndRegion

#Region Service

Function RemoveNonNumbers(Str, WithDelimiter=False)

	If IsBlankString(Str) Then
		Return "";
	EndIf;

	Result = "";
	HasDelimiter = False;

	L = StrLen(Str);

	For i=1 To L Do

		C = Mid(Str, i, 1);

		If NOT HasDelimiter AND WithDelimiter Then

			If C = "." OR C = "," Then

				Result = "" + Result + ".";
				HasDelimiter = True;
				Continue;

			EndIf;

		EndIf;

		If Find("0123456789", C) > 0 Then

			Result = "" + Result + C;

		EndIf;

	EndDo;

	If Left(Result, 1) = "." Then

		Result = "0" + Result;

	EndIf;

	If Right(Result, 1) = "." Then

		Result = "" + Result + "0";

	EndIf;

	Return Result;

EndFunction // RemoveNonNumbers()

Function ContainsNonNumbers(Str)

	If IsBlankString(Str) Then

		Return True;

	EndIf;

	For I=1 To StrLen(Str) Do

		If StrFind("1234567890", Mid(Str, I, 1)) = 0 Then

			Return True;

		EndIf;

	EndDo;

	Return False;

EndFunction // ContainsNonNumbers()

#EndRegion

#Region JobExecution

// Проверяет, выполняется ли сейчас фоновое задание обмена.
// Используется для защиты от параллельных выгрузок, запущенных интерактивно:
// сервер обмена требует не более одного push в полете на устройство.
Function ExchangeJobActive() Export

	Filter = New Structure;
	Filter.Insert("MethodName", "AV_Common.ExchangeJob");
	Filter.Insert("State", BackgroundJobState.Active);

	Return BackgroundJobs.GetBackgroundJobs(Filter).Count() > 0;

EndFunction // ExchangeJobActive()

Procedure ExchangeJob(Params) Export
	Var Uid, Action;

	If TypeOf(Params) <> Type("Structure") Then

		//Utils.Debug(, "Job", "Unsupported parameter type in Exchange Job: " + TypeOf(Params));
		Return;

	EndIf;

	Params.Property("Uid", Uid);
	Params.Property("Action", Action);

	//------------------------ загрузка заказов; один запрос по всем агентам
	Query = New Query;
	Query.Text =
	"SELECT
	|	AV_Settings.Token AS Token,
	|	AV_Settings.Server AS Server,
	|	AV_Settings.Ref AS Ref
	|FROM
	|	Catalog.AV_Settings AS AV_Settings
	|WHERE
	|	NOT AV_Settings.DeletionMark
	|	AND (AV_Settings.JobDiff = &Uid
	|			OR AV_Settings.JobFullUpload = &Uid)";

	Query.SetParameter("Uid", Uid);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		WriteLogEvent("AgentVenta", EventLogLevel.Note, , Sel.Ref, "Action: ORDER_DOWNLOAD");

		Params.Insert("Token", 		Sel.Token);
		Params.Insert("Server",		Sel.Server);
		Params.Insert("Err",		Undefined);
		Params.Insert("Response",	Undefined);
		Params.Insert("Conn",		Undefined);

		DeviceDataDownload(Params);

		// сбой приёма заказов иначе невиден: pull ставит Params.Err и молча выходит
		// (транспортный обрыв GET не ретраится, 429 — после исчерпания попыток),
		// а документы остаются в очереди на relay. Фиксируем причину в журнале.
		If Params.Err <> Undefined Then
			WriteLogEvent("AgentVenta", EventLogLevel.Error, , Sel.Ref,
				StrTemplate("ORDER_DOWNLOAD failed: %1", Params.Err));
		EndIf;

	EndDo;

	//------------------------ выгрузка полная или частичная
	// Сортировка по Ref критична: устройства одной AV_Settings разделяют один план обмена,
	// и регистрации изменений можно удалять только один раз — после обработки всей группы.
	Query = New Query;
	Query.Text =
	"SELECT ALLOWED
	|	AV_SettingsDevices.Ref.Token AS Token,
	|	AV_SettingsDevices.Ref AS Ref,
	|	AV_SettingsDevices.ID AS ID,
	|	AV_SettingsDevices.Name AS Name,
	|	AV_SettingsDevices.Manager AS Manager,
	|	AV_SettingsDevices.Ref.ExchangePlan AS ExchangePlan,
	|	AV_SettingsDevices.Ref.Server AS Server,
	|	AV_SettingsDevices.Ref.Debug AS Debug,
	|	AV_SettingsDevices.Ref.DebugPath AS DebugPath
	|FROM
	|	Catalog.AV_Settings.Devices AS AV_SettingsDevices
	|WHERE
	|	AV_SettingsDevices.Enabled
	|	AND NOT AV_SettingsDevices.Ref.DeletionMark
	|	AND (AV_SettingsDevices.Ref.JobFullUpload = &Uid
	|			OR AV_SettingsDevices.Ref.JobDiff = &Uid)
	|
	|ORDER BY
	|	AV_SettingsDevices.Ref";

	Query.SetParameter("Uid", Uid);

	Sel = Query.Execute().Select();

	// Состояние текущей группы устройств (devices одной AV_Settings):
	//   GroupRef     — Ref AV_Settings, к которой относятся устройства группы
	//   GroupNode    — план обмена группы (общий для всех её устройств)
	//   GroupFailed  — был ли отказ хоть на одном устройстве группы
	//   GroupChanges — единожды прочитанный набор изменений плана обмена
	GroupRef		= Undefined;
	GroupNode		= Undefined;
	GroupFailed		= False;
	GroupChanges	= Undefined;

	While Sel.Next() Do

		// Завершение предыдущей группы при смене Ref
		If GroupRef <> Undefined AND Sel.Ref <> GroupRef Then
			FinishDiffGroup(GroupNode, GroupChanges, GroupFailed);
			GroupFailed		= False;
			GroupChanges	= Undefined;
		EndIf;

		// Новая группа: один раз читаем регистрации изменений для всего плана обмена.
		// Читаем и для FULL_UPLOAD (п.24): после полной выгрузки существовавшие
		// до её начала регистрации избыточны и очищаются в FinishDiffGroup при
		// успехе — иначе следующий diff повторно перешлёт уже доставленные данные.
		// Изменения, зарегистрированные во время выгрузки, в снимок не попадают и
		// уцелеют для следующего diff.
		If Sel.Ref <> GroupRef Then
			GroupChanges = FetchDiffChanges(Sel.ExchangePlan);
		EndIf;

		GroupRef	= Sel.Ref;
		GroupNode	= Sel.ExchangePlan;

		Text = StrTemplate("Action: %1; Name: %2; id=%3***", Action, Sel.Name, Mid(Sel.ID, 1, 8));
		WriteLogEvent("AgentVenta", EventLogLevel.Note, , Sel.Ref, Text);

		Params.Insert("DeviceID", 	Sel.ID);
		Params.Insert("Name", 		Sel.Name);
		Params.Insert("Manager",	Sel.Manager);
		Params.Insert("Token", 		Sel.Token);
		Params.Insert("Server",		Sel.Server);
		Params.Insert("Debug",		Sel.Debug);
		Params.Insert("DebugPath",	Sel.DebugPath);
		Params.Insert("Err",		Undefined);
		Params.Insert("Response",	Undefined);
		Params.Insert("Conn",		Undefined);

		// план обмена для выгрузки изменений
		Params.Insert("ExchangePlan", Sel.ExchangePlan);

		// заглушки для фильтров по изменениям
		Params.Insert("FProducts", 	Undefined);
		Params.Insert("FImages", 	Undefined);
		Params.Insert("FClients", 	Undefined);

		// Очищаем результат предыдущего устройства, чтобы не перепутать наборы
		If Params.Property("DiffObjectsToCleanup") Then
			Params.Delete("DiffObjectsToCleanup");
		EndIf;

		If Action = "FULL_UPLOAD" Then

			DeviceDataUpload(Params);

		ElsIf Action = "DIFF_UPLOAD" Then

			DiffUpload(Params, GroupChanges);

		EndIf;

		If Params.Err<>Undefined Then

			WriteLogEvent("AgentVenta", EventLogLevel.Error, , Sel.Ref, Params.Err);
			GroupFailed = True;

		EndIf;

	EndDo;

	// Завершаем последнюю группу
	If GroupRef <> Undefined Then
		FinishDiffGroup(GroupNode, GroupChanges, GroupFailed);
	EndIf;

EndProcedure

// Удаляет регистрации изменений для группы устройств одной AV_Settings,
// если ни одно устройство группы не сообщило об ошибке.
// При сбое регистрации сохраняются и попадут в следующий diff.
Procedure FinishDiffGroup(Node, Changes, GroupFailed) Export

	If GroupFailed Then
		Return;
	EndIf;

	If NOT ValueIsFilled(Node) Then
		Return;
	EndIf;

	If Changes = Undefined Then
		Return;
	EndIf;

	For Each Obj In Changes.Objects Do
		ExchangePlans.DeleteChangeRecords(Node, Obj);
	EndDo;

EndProcedure

#EndRegion