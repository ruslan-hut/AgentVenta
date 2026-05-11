
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

	If NOT SSL Then

		Return New HTTPConnection(Url, ?(Port=0, 80, Port));

	EndIf;

	Return New HTTPConnection(Url, ?(Port=0, 443, Port),,,,, New OpenSSLSecureConnection);

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

Function GetProductPage(Params, Next)

	Data 		= New Array;
	PageSize 	= 100;
	From		= PageSize * Next;
	Count 		= Format(PageSize * (Next + 1), "NG=0");
	N 			= 0;

	Query = New Query;
	Query.Text =
	"SELECT TOP "+Count+"
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
	|
	|ORDER BY
	|	Ref";

	Query.SetParameter("WithFilter", 	Params.FProducts<>Undefined);
	Query.SetParameter("Filter", 		Params.FProducts);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		If N >= From Then

			Data.Add(Sel.Ref);

		EndIf;

		N = N + 1;

	EndDo;

	If Data.Count() = 0 Then
		Next = 0;
	Else
		Next = Next + 1;
	EndIf;

	Return Data;

EndFunction // GetProductPage()

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

Function GetClientPage(Params, Next)

	Data 		= New Array;
	PageSize 	= 100;
	From		= PageSize * Next;
	Count 		= Format(PageSize * (Next + 1), "NG=0");
	N 			= 0;

	Query = New Query;
	Query.Text =
	"SELECT TOP "+Count+"
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
	|
	|ORDER BY
	|	Ref";

	Query.SetParameter("Manager", 		Params.Manager);
	Query.SetParameter("WithFilter", 	Params.FClients<>Undefined);
	Query.SetParameter("Filter", 		Params.FClients);

	Sel = Query.Execute().Select();

	While Sel.Next() Do

		If N >= From Then

			Data.Add(Sel.Ref);

		EndIf;

		N = N + 1;

	EndDo;

	If Data.Count() = 0 Then
		Next = 0;
	Else
		Next = Next + 1;
	EndIf;

	Return Data;

EndFunction // GetClientPage()

Function GetClientData(Params, Next)

	Data = New Array;

	TPhone = Catalogs.ВидыКонтактнойИнформации.ТелефонКонтрагента;
	TAddress = Catalogs.ВидыКонтактнойИнформации.АдресДоставкиКонтрагета;

	Query = New Query;
	Query.Text =
	"SELECT
	|	Контрагенты.Ref AS Ref,
	|	Контрагенты.IsFolder AS IsFolder,
	|	Контрагенты.Code AS Code,
	|	Контрагенты.Parent AS Parent,
	|	Контрагенты.Description AS Description,
	|	Контрагенты.ДоговорПоУмолчанию.ВидЦен AS PriceType,
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

	Query.SetParameter("Filter", GetClientPage(Params, Next));

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
		Item.Insert("price_type", 		String(Sel.PriceType.UUID()));
		Item.Insert("sum", 				0);

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

#EndRegion

#Region DataUpload

Procedure DeviceDataUpload(Params) Export

	// метка времени в миллисекундах для отметки всех элементов данных
	Params.Insert("Timestamp", CurrentUniversalDateInMilliseconds());

	// заглушки для фильтров по изменениям
	Params.Insert("FProducts", 	Undefined);
	Params.Insert("FClients", 	Undefined);

	UserOptions(Params);

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	SendProducts(Params);

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

	FinishFullUpload(Params);

EndProcedure

Procedure DeviceOptionsUpload(Params) Export

	// метка времени в миллисекундах для отметки всех элементов данных
	Params.Insert("Timestamp", 	CurrentUniversalDateInMilliseconds());
	Params.Insert("Conn", 		Undefined);

	UserOptions(Params);

	Params.Insert("Conn", 		Undefined);

EndProcedure

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
	Opt.Insert("loadImages",			False);
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
	Opt.Insert("useCompanies", 			False);
	// работа с несколькими складами
	Opt.Insert("useStores", 			False);


	Name = Params.Name;
	If IsBlankString(Name) Then
		Name = String(Params.Manager);
	EndIf;
	Opt.Insert("name", Name);

	Data = New Structure;
	Data.Insert("device_uuid", Params.DeviceID);

	// если задать ид сообщения, оно перезапишет предыдущее сообщение с этим ид
	Data.Insert("message_uuid", Params.DeviceID);

	Data.Insert("Data", New Array);
	Data.Data.Add(Opt);

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
//   Objects  — Array(СправочникОбъект/НаборЗаписей) — для последующей очистки регистраций
//   Products — Array(СправочникСсылка.Номенклатура) — фильтр для SendProducts
//   Clients  — Array(СправочникСсылка.Контрагенты)  — фильтр для SendClients / SendDiscounts
//
Function FetchDiffChanges(Node) Export

	Changes = New Structure;
	Changes.Insert("Objects",  New Array);
	Changes.Insert("Products", New Array);
	Changes.Insert("Clients",  New Array);

	If NOT ValueIsFilled(Node) Then
		Return Changes;
	EndIf;

	// Карты используются как множества для O(1) проверки дубликатов
	ProductsMap = New Map;
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

		Else

			// прочее (включая ВидыЦенМенеджеров) — не отправляется,
			// но регистрации очищаются вместе с группой
			Changes.Objects.Add(Obj);

		EndIf;

	EndDo;

	Return Changes;

EndFunction

Procedure DiffUpload(Params, Changes)

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

	If Changes.Products.Count() > 0 Then

		Params.Insert("FProducts", Changes.Products);

		SendProducts(Params);

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

	EndIf;

	// Список изменений, накопленных для текущего плана обмена.
	// Очистка регистраций выполняется в ExchangeJob после успешной обработки
	// всех устройств, привязанных к этому же плану (AV_Settings.Ref).
	Params.Insert("DiffObjectsToCleanup", Changes.Objects);

EndProcedure

#EndRegion

#Region DataDownload

Procedure DeviceDataDownload(Params) Export

	GET(Params, "pull");

	If Params.Err<>Undefined Then
		Return;
	EndIf;

	If Params.Response = Undefined Then
		Params.Err = "Empty response";
		Return;
	EndIf;

	Data = Params.Response["data"];

	Count = Data["count"];

	If Count<>Undefined AND Count = 0 Then
		Message("Device data queue is EMPTY");
		Return;
	Else
		Message("Received data elements: "+Count);
	EndIf;

	Items = Data["items"];

	For Each Item In Items Do

		DataType = Item["data_type"];

		If DataType = "order" Then

			DownloadOrder(Item["data"], Params);

		Else

			Message("Unsupported data type: "+DataType);

		EndIf;

	EndDo;

EndProcedure

Procedure DownloadOrder(Order, Params)

	// дата документа указана в данных в виде timestamp в секундах
	// нужно посчитать от начала эпохи Unix
	BaseDate = Date(1970, 1, 1, 0, 0, 0);

	Manager = GetManager(Order["userID"]);

	If Manager = Undefined Then

		Txt = "Невідомий пристрій, документ пропущений; ID=" + Order["userID"];

		WriteLogEvent("AgentVenta", EventLogLevel.Error,,, Txt);
		Message(Txt);
		Return;

	EndIf;

	///////////////////////////////////////////// ссылка на документ
	UID = Order["guid"];

	Try

		Ref = Documents.ЗаказПокупателя.GetRef(New UUID(UID));

	Except
	    Message(StrTemplate("id=%1; %2", UID, BriefErrorDescription(ErrorInfo())));
		Return;
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
		Return;

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
		Return;
	EndTry;

	Try

		Doc.Write(DocumentWriteMode.Posting);
		Message("Order posted: "+Doc.Ref);

	Except
		Txt = StrTemplate("%1: post error: %2", Doc.Ref, BriefErrorDescription(ErrorInfo()));
		Message(Txt);
		WriteLogEvent("AgentVenta", EventLogLevel.Error,, Doc.Ref, Txt);
		Return;
	EndTry;

EndProcedure

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

Procedure DoRequest(Params, Path, Data)
	Var Err, StatusCode;

	If NOT Params.Property("CC") Then
		Params.Insert("CC", 0);
	EndIf;

	LogPath 	= "E:\dev\debug\";
	Debug 		= True;
	CC 			= Params.CC + 1;
	Params.CC 	= CC;

	If CC%90 = 0 Then
		WriteLogEvent("AgentVenta", EventLogLevel.Note, , , "Sleep; count="+CC);
		Sleep(60);
	EndIf;

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

		Params.Conn = HTTP_NewConnection("https://lic.nomadus.net", , True);

	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = StrTemplate("/api/v1/%1", Path);

	If Debug Then

		JsonErr = Undefined;
		FileName = StrTemplate("%1request_%2.txt", LogPath, CC);

		Txt = New TextDocument;
		Txt.SetText(Req.GetBodyAsString());
		Txt.Write(FileName);

	EndIf;

	// Повторы при HTTP 429 (Too Many Requests).
	// Паузы между попытками: 10, 20, 30 секунд — итого до 3 повторов после первой неудачи.
	RetryDelays = New Array;
	RetryDelays.Add(10);
	RetryDelays.Add(20);
	RetryDelays.Add(30);

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

Procedure GET(Params, Path)
	Var Err, StatusCode;

	Params.Insert("Response", Undefined);

	Req = New HTTPRequest();

	If Params.Conn = Undefined Then

		Params.Conn = HTTP_NewConnection("https://lic.nomadus.net", , True);

	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = StrTemplate("/api/v1/%1", Path);

	Response = HTTP_GET(Params.Conn, , Req, StatusCode, Err);

	If Err<>Undefined Then

		Params.Err = "HTTP request: " + Err;
		Return;

	EndIf;

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

		Params.Conn = HTTP_NewConnection("https://lic.nomadus.net", , True);

	EndIf;

	Req.Headers.Insert("Authorization", StrTemplate("Bearer %1", Params.Token));
	Req.ResourceAddress = StrTemplate("/api/v1/%1", Path);

	Response = HTTP_DELETE(Params.Conn, , Req, StatusCode, Err);

	If Err<>Undefined Then

		Params.Err = "HTTP request: " + Err;
		Return;

	EndIf;

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

Procedure ReadDevices(Token, Response) Export

	Params = New Structure;
	Params.Insert("Conn", 		Undefined);
	Params.Insert("Token", 		Token);
	Params.Insert("Err",		Undefined);
	Params.Insert("Response",	Undefined);

	GET(Params, "devices");

	If Params.Err<>Undefined Then
		Message(Params.Err);
	EndIf;

	Response = Params.Response;

EndProcedure

Procedure ApproveDevice(Token, ID, Err) Export

	Params = New Structure;
	Params.Insert("Conn", 		Undefined);
	Params.Insert("Token", 		Token);
	Params.Insert("Err",		Undefined);
	Params.Insert("Response",	Undefined);

	Data = New Structure;
	Data.Insert("device_uuid", ID);

	DoRequest(Params, "devices/register", Data);

	Err = Params.Err;

EndProcedure

Procedure RemoveDevice(Token, ID, Err) Export

	Params = New Structure;
	Params.Insert("Conn", 		Undefined);
	Params.Insert("Token", 		Token);
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
		Params.Insert("Err",		Undefined);
		Params.Insert("Response",	Undefined);
		Params.Insert("Conn",		Undefined);

		DeviceDataDownload(Params);

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
	|	AV_SettingsDevices.Ref.ExchangePlan AS ExchangePlan
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

		// Новая группа: один раз читаем регистрации изменений для всего плана обмена
		If Sel.Ref <> GroupRef Then
			If Action = "DIFF_UPLOAD" Then
				GroupChanges = FetchDiffChanges(Sel.ExchangePlan);
			EndIf;
		EndIf;

		GroupRef	= Sel.Ref;
		GroupNode	= Sel.ExchangePlan;

		Text = StrTemplate("Action: %1; Name: %2; id=%3***", Action, Sel.Name, Mid(Sel.ID, 1, 8));
		WriteLogEvent("AgentVenta", EventLogLevel.Note, , Sel.Ref, Text);

		Params.Insert("DeviceID", 	Sel.ID);
		Params.Insert("Name", 		Sel.Name);
		Params.Insert("Manager",	Sel.Manager);
		Params.Insert("Token", 		Sel.Token);
		Params.Insert("Err",		Undefined);
		Params.Insert("Response",	Undefined);
		Params.Insert("Conn",		Undefined);

		// план обмена для выгрузки изменений
		Params.Insert("ExchangePlan", Sel.ExchangePlan);

		// заглушки для фильтров по изменениям
		Params.Insert("FProducts", 	Undefined);
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
Procedure FinishDiffGroup(Node, Changes, GroupFailed)

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