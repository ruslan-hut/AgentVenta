package ua.com.programmer.agentventa.documents;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import ua.com.programmer.agentventa.R;
import ua.com.programmer.agentventa.data.DataBase;

public class DialogPriceTypeChoose extends DialogFragment {

    private String[] priceTypes;
    private Context mContext;

    public interface DialogPriceTypeChooseListener{
        void onPriceTypeChoose(int priceType);
    }

    private DialogPriceTypeChooseListener listener;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.price_type)
                .setItems(priceTypes, (dialog, which) -> {
                    listener.onPriceTypeChoose(DataBase.getInstance(mContext).priceTypeCode(which));
                });
        return builder.create();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        mContext = context;
        priceTypes = DataBase.getInstance(mContext).getPriceTypes();
        super.onAttach(mContext);
        listener = (DialogPriceTypeChooseListener) mContext;
    }

}
