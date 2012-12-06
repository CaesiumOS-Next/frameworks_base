
package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.ImageButton;
import com.android.internal.telephony.RILConstants.SimCardID;

public class SimImageButton extends ImageButton {
    private final static String TAG = "SimImageButton";

    private final static int X_START_POSITON = 15;
    private final static int Y_START_POSITON = 20;
    private final static int Y_BUTTOM_MARGIN = 10;
    private final static int TEXT_SIZE = 10;

    private Context mContext;
    private Paint mPaint;
    private String mType = "";
    private String mNumber = "";

    public SimImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Style.FILL);
    }

    public void updateInfo(TelephonyManager tm) {
        if (tm == null)
            return;

        if (TelephonyManager.NETWORK_CLASS_2_G == tm.getNetworkClass(tm.getNetworkType())) {
            mType = "2G";
        } else if (TelephonyManager.NETWORK_CLASS_3_G == tm.getNetworkClass(tm.getNetworkType())) {
            mType = "3G";
        } else if (TelephonyManager.NETWORK_CLASS_4_G == tm.getNetworkClass(tm.getNetworkType())) {
            mType = "4G";
        } else {
            mType = "";
        }

        mNumber = tm.getLine1Number();
        String displayNumberMode;
        if (SimCardID.ID_ONE == tm.getSimCardId()) {
            displayNumberMode = Settings.Global.DISPLAY_SIM2_NUMBER_MODE;
        } else {
            displayNumberMode = Settings.Global.DISPLAY_SIM_NUMBER_MODE;
        }
        int displayType = Settings.Secure.getInt(mContext.getContentResolver(),
                displayNumberMode, 0);

        if (mNumber != null)
            mNumber = getAbbrNumber(mNumber, displayType);

        if (mNumber == null)
            mNumber = "";
    }

    private String getAbbrNumber(String number, int abbrType) {
        String abbrNumber = null;

        if (number == null || number.length() < 4) {
            android.util.Log.e(TAG, "getAbbrNumber():Error and invalid number ");
            return null;
        }

        if (2 == abbrType) {
            abbrNumber = null;
        } else if (1 == abbrType) {
            int len = number.length();
            abbrNumber = number.substring(len - 4, len);
        } else {
            // TODO: temp only handle chinese international prefix code
            if (number.startsWith("+86") && number.length() >= 7) {
                abbrNumber = number.substring(3, 7);
            } else {
                abbrNumber = number.substring(0, 4);
            }
        }

        return abbrNumber;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        android.util.Log.d(TAG, "onDraw(): mType=" + mType
                + ", mNumber=" + mNumber);

        int height = getBottom() - getTop();
        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int x = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                X_START_POSITON, displayMetrics);
        int y_type = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                Y_START_POSITON, displayMetrics);
        int y_num = height - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                Y_BUTTOM_MARGIN, displayMetrics);
        int textSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TEXT_SIZE, displayMetrics);

        mPaint.setTextSize(textSize);
        canvas.drawText(mType, x, y_type, mPaint);
        mPaint.setTextSize(textSize);
        canvas.drawText(mNumber, x, y_num, mPaint);
    }
}
