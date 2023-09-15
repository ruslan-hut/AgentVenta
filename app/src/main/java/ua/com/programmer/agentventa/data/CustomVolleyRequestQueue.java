package ua.com.programmer.agentventa.data;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CustomVolleyRequestQueue {

    private static CustomVolleyRequestQueue INSTANCE;
    private File cacheDir;
    private RequestQueue requestQueue;
    private ImageLoader imageLoader;
    private String USER_PASSWORD="";

    private CustomVolleyRequestQueue(File cacheDir, String password){
        this.cacheDir = cacheDir;
        this.USER_PASSWORD = password;
        requestQueue = getRequestQueue();

        imageLoader = new ImageLoader(requestQueue,
                new ImageLoader.ImageCache() {
                    private final LruCache<String,Bitmap> cache = new LruCache<>(5000);

                    @Override
                    public Bitmap getBitmap(String url) {
                        return cache.get(url);
                    }

                    @Override
                    public void putBitmap(String url, Bitmap bitmap) {
                        cache.put(url,bitmap);
                    }
                }){
            @Override
            protected Request<Bitmap> makeImageRequest(String requestUrl, int maxWidth, int maxHeight, ImageView.ScaleType scaleType, String cacheKey) {
                return new ImageRequest(
                        requestUrl,
                        response -> onGetImageSuccess(cacheKey, response),
                        maxWidth,
                        maxHeight,
                        scaleType,
                        Bitmap.Config.RGB_565,
                        error -> onGetImageError(cacheKey,error)){

                    @Override
                    public Map<String, String> getHeaders() {
                        return authHeaders();
                    }
                };
            }
        };
    }

    public static synchronized CustomVolleyRequestQueue getInstance(File cacheDir, String password){
        if (INSTANCE == null){
            INSTANCE = new CustomVolleyRequestQueue(cacheDir,password);
        }
        return INSTANCE;
    }

    HashMap<String, String> authHeaders(){
        HashMap<String, String> headers = new HashMap<>();
        String credentials = USER_PASSWORD;
        String auth = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
        headers.put("Authorization", auth);
        return headers;
    }

    private RequestQueue getRequestQueue(){
        if (requestQueue == null){
            Cache cache = new DiskBasedCache(cacheDir,100*1024*1024);
            Network network = new BasicNetwork(new HurlStack());
            requestQueue = new RequestQueue(cache,network);
            requestQueue.start();
        }
        return requestQueue;
    }

    public ImageLoader getImageLoader(){
        return imageLoader;
    }
}
