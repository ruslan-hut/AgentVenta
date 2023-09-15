package ua.com.programmer.agentventa.catalogs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.igreenwood.loupe.Loupe;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.utility.ImageLoader;

public class GoodsImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.product_image_fragment);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ViewGroup imageContainer = findViewById(R.id.image_container);
        ImageView imageView = findViewById(R.id.item_image);

        Intent intent = getIntent();
        String imageGUID = intent.getStringExtra("imageGUID");
        String itemGUID = intent.getStringExtra("itemGUID");

        GoodsItem item = GoodsItem.getInstance(this).initialize(itemGUID,0);
        setTitle(item.description);

        ImageLoader imageLoader = new ImageLoader(this);
        imageLoader.load(imageGUID,imageView);

        Loupe loupe = new Loupe(imageView,imageContainer);
        loupe.setOnViewTranslateListener(new Loupe.OnViewTranslateListener() {
            @Override
            public void onStart(@NonNull ImageView imageView) {

            }

            @Override
            public void onViewTranslate(@NonNull ImageView imageView, float v) {

            }

            @Override
            public void onDismiss(@NonNull ImageView imageView) {
                finish();
            }

            @Override
            public void onRestore(@NonNull ImageView imageView) {

            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) onBackPressed();
        return super.onOptionsItemSelected(item);
    }
}