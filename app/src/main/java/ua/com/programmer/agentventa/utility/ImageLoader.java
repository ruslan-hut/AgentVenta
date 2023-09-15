package ua.com.programmer.agentventa.utility;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.signature.ObjectKey;

import java.io.File;
import java.util.ArrayList;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;
import ua.com.programmer.agentventa.settings.AppSettings;

public class ImageLoader {

    private final RequestManager requestManager;
    private String baseImageURL;
    private LazyHeaders authHeaders;

    private final DataBase dataBase;
    private final AppSettings appSettings;

    public ImageLoader(Context context){
        requestManager = Glide.with(context);
        appSettings = AppSettings.getInstance(context);
        dataBase = DataBase.getInstance(context);
    }

    /**
     * Initialise environment parameters for load requests.
     */
    private void init(){
        baseImageURL = appSettings.getBaseImageUrl();
        authHeaders = appSettings.getAuthHeaders();
    }

    /**
     * Image parameters has two keys, if a direct URL is given, than using it,
     * otherwise using image GUID with the base URL combined
     *
     * @param imageParameters data set of image parameters
     * @return image URL
     */
    private String imageURL(DataBaseItem imageParameters){

        String url = imageParameters.getString("image_url");
        if (url.isEmpty()) url = imageParameters.getString("url");
        if (!url.isEmpty()) return url;

        String imageGUID = imageParameters.getString("image_guid");
        if (!imageGUID.isEmpty()) return baseImageURL + imageGUID;

        return "";
    }

    /**
     * Load image data with given parameters into given ImageView
     *
     * @param imageParameters parameters of an image
     * @param view image showing view
     */
    public void load(DataBaseItem imageParameters, ImageView view){
        init();

        String url = imageURL(imageParameters);

        if (!url.isEmpty()) {
            view.setVisibility(View.VISIBLE);
            GlideUrl glideUrl = new GlideUrl(url,authHeaders);
            requestManager.load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.picture_blank_150)
                    .error(R.drawable.ic_error_gray_64dp)
                    .into(view);
        }else {
            view.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Load image by GUID into given ImageView. Image parameters is retrieved from the database
     *
     * @param imageGUID image GUID
     * @param view image showing view
     */
    public void load(String imageGUID, ImageView view){
        init();
        DataBaseItem imageParameters = dataBase.getImage(imageGUID);
        load(imageParameters,view);
    }

    /**
     * Loading images into disk cache. If image data contains an "url", loading via direct url.
     * Otherwise loading via image GUID.
     */
    public void requestImages(){
        init();

        Thread thread = new Thread(){
            @Override
            public void run() {

                Utils utils = new Utils();
                String url;

                //Glide.get(context).clearDiskCache();

                long time = utils.currentTime();
                ArrayList<DataBaseItem> imageList = dataBase.getImages();

                for (DataBaseItem image: imageList){
                    url = imageURL(image);
                    if (url.isEmpty()) continue;
                    GlideUrl glideUrl = new GlideUrl(url,authHeaders);
                    FutureTarget<File> future = requestManager.downloadOnly()
                            .signature(new ObjectKey(image.getString("time")))
                            .load(glideUrl)
                            .submit();
                    try {
                        future.get();
                    }catch (Exception e){
                        utils.warn("get image file; url: "+url+"; "+e.toString());
                    }
                }
                utils.debug("Loaded "+imageList.size()+" images in "+utils.showTime(time,utils.currentTime()));
            }
        };
        thread.start();

    }

    /**
     * Stop all current and pending requests on activity onDestroy event.
     */
    public void stop(){
        if (requestManager != null) requestManager.pauseAllRequests();
    }

}
