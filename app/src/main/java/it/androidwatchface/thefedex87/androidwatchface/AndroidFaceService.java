package it.androidwatchface.thefedex87.androidwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.rendering.ComplicationDrawable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.support.wearable.complications.ComplicationData.TYPE_ICON;
import static android.support.wearable.complications.ComplicationData.TYPE_RANGED_VALUE;
import static android.support.wearable.complications.ComplicationData.TYPE_SHORT_TEXT;
import static android.support.wearable.complications.SystemProviders.WATCH_BATTERY;
import static android.support.wearable.complications.SystemProviders.batteryProvider;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class AndroidFaceService extends CanvasWatchFaceService {
    private static final String TAG = AndroidFaceService.class.getSimpleName();
    // TODO: Step 2, intro 1
    private static final int LEFT_COMPLICATION_ID = 0;


    private static final int[] COMPLICATION_IDS = {LEFT_COMPLICATION_ID};

    // Left and right dial supported types.
    private static final int[][] COMPLICATION_SUPPORTED_TYPES = {
            {
                    ComplicationData.TYPE_RANGED_VALUE,
                    TYPE_ICON,
                    TYPE_SHORT_TEXT,
                    ComplicationData.TYPE_SMALL_IMAGE
            }
    };
    private SparseArray<ComplicationData> mActiveComplicationDataSparseArray;

    private SparseArray<ComplicationDrawable> mComplicationDrawableSparseArray;

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<AndroidFaceService.Engine> mWeakReference;

        public EngineHandler(AndroidFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            AndroidFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {



        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mXOffset;
        private float mYOffset;
        private Paint mBackgroundPaint;
        private Paint mTextPaint;
        private Bitmap mBackgroundAndroidBitmap;
        private Bitmap mBackgroundBitmap;
        private float mScale;

        private Paint mComplicationTextPaint;

        private String batteryLevel = "100";

        private Bitmap mComplicationContainer;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(AndroidFaceService.this)
                    .setAcceptsTapEvents(true)
                    .build());

            mCalendar = Calendar.getInstance();

            Resources resources = AndroidFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.background));


            // Initializes Watch Face.
            mTextPaint = new Paint();
            //mTextPaint.setTypeface(NORMAL_TYPEFACE);
            mTextPaint.setTypeface(ResourcesCompat.getFont(getBaseContext(), R.font.font2));
            mTextPaint.setAntiAlias(true);
            mTextPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mComplicationTextPaint = new Paint();
            mComplicationTextPaint.setTypeface(ResourcesCompat.getFont(getBaseContext(), R.font.font2));
            mComplicationTextPaint.setAntiAlias(true);
            mComplicationTextPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
            mComplicationTextPaint.setTextSize(20);


            //Initialize complications
            initializeComplications();

            //Initialize the background bitmap
            mBackgroundAndroidBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg2);

            mComplicationContainer = BitmapFactory.decodeResource(getResources(), R.drawable.complication_frame);
            setDefaultSystemComplicationProvider(LEFT_COMPLICATION_ID, WATCH_BATTERY, TYPE_RANGED_VALUE);
        }

        // TODO: Step 2, initializeComplications()
        private void initializeComplications() {
            Log.d(TAG, "initializeComplications()");

            mActiveComplicationDataSparseArray = new SparseArray<>(COMPLICATION_IDS.length);

            ComplicationDrawable leftComplicationDrawable = (ComplicationDrawable) getDrawable(R.drawable.custom_complication_style);
            leftComplicationDrawable.setContext(getApplicationContext());

            mComplicationDrawableSparseArray = new SparseArray<>(COMPLICATION_IDS.length);
            mComplicationDrawableSparseArray.put(LEFT_COMPLICATION_ID, leftComplicationDrawable);

            setActiveComplications(COMPLICATION_IDS);
        }

        @Override
        public void onComplicationDataUpdate(
                int complicationId, ComplicationData complicationData) {
            Log.d(TAG, "onComplicationDataUpdate() id: " + complicationId + ". Data Value: " + String.valueOf(complicationData.getValue()));

            // Adds/updates active complication data in the array.
            mActiveComplicationDataSparseArray.put(complicationId, complicationData);

            batteryLevel = String.valueOf((int)complicationData.getValue());

            // Updates correct ComplicationDrawable with updated data.
            ComplicationDrawable complicationDrawable =
                    mComplicationDrawableSparseArray.get(complicationId);
            complicationDrawable.setComplicationData(complicationData);

            invalidate();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mScale = ((float)width) / ((mBackgroundAndroidBitmap.getWidth()));

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int)(mBackgroundBitmap.getWidth() * mScale),
                    (int)(mBackgroundBitmap.getHeight() * mScale),
                    true);

            mScale *= 0.7;

            mBackgroundAndroidBitmap = Bitmap.createScaledBitmap(mBackgroundAndroidBitmap,
                    (int)(mBackgroundAndroidBitmap.getWidth() * mScale),
                    (int)(mBackgroundAndroidBitmap.getHeight() * mScale),
                    true);


            mComplicationContainer = Bitmap.createScaledBitmap(mComplicationContainer,
                    (int)(width * 0.2f),
                    (int)(width * 0.2f),
                    true);

//            // TODO: Step 2, calculating ComplicationDrawable locations
            int sizeOfComplication = width / 6;
            int midpointOfScreen = width / 2;

            int horizontalOffset = (width - sizeOfComplication) / 8;
            int verticalOffset = midpointOfScreen + (sizeOfComplication / 2) - (int)(width * 0.03f);

            Rect leftBounds =
                    // Left, Top, Right, Bottom
                    new Rect(
                            horizontalOffset,
                            verticalOffset,
                            (horizontalOffset + sizeOfComplication),
                            (verticalOffset + sizeOfComplication));

            ComplicationDrawable leftComplicationDrawable =
                    mComplicationDrawableSparseArray.get(LEFT_COMPLICATION_ID);
            leftComplicationDrawable.setBounds(leftBounds);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            AndroidFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AndroidFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = AndroidFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTextPaint.setAntiAlias(!inAmbientMode);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                //canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                canvas.drawBitmap(mBackgroundBitmap, (canvas.getWidth() - mBackgroundBitmap.getWidth()) /2 , (canvas.getHeight() - mBackgroundBitmap.getHeight()) /2 * 1.3f, mBackgroundPaint);
                canvas.drawBitmap(mBackgroundAndroidBitmap, (canvas.getWidth() - mBackgroundAndroidBitmap.getWidth()) /2 , (canvas.getHeight() - mBackgroundAndroidBitmap.getHeight()) /2 * 1.3f, mBackgroundPaint);


                mComplicationDrawableSparseArray.get(0).draw(canvas, System.currentTimeMillis());

                canvas.drawBitmap(mComplicationContainer, (canvas.getWidth() - mComplicationContainer.getWidth()) / 2 , canvas.getHeight() * 0.68f - (mComplicationContainer.getHeight() / 2), mBackgroundPaint);
                int midXComplicationBattery = canvas.getWidth() / 2;
                int midYComplicationBattery = (int)(canvas.getHeight() * 0.68f);

                String batteryLevelStr = batteryLevel + "%";
                Rect textBoundsBatteryLevel = new Rect();
                mComplicationTextPaint.getTextBounds(batteryLevelStr, 0, batteryLevelStr.length(), textBoundsBatteryLevel);
                //Log.d(TAG, String.valueOf(midXComplicationBattery)+"_"+String.valueOf(midYComplicationBattery)+"_"+String.valueOf(textBoundsBatteryLevel.width())+"_"+String.valueOf(textBoundsBatteryLevel.height()));
                canvas.drawText(batteryLevelStr, midXComplicationBattery - textBoundsBatteryLevel.width() / 2, midYComplicationBattery + textBoundsBatteryLevel.height() / 2, mComplicationTextPaint);

//                Paint p = new Paint();
//                p.setColor(Color.BLACK);
//                p.setAntiAlias(true);
//
//                canvas.drawCircle(midXComplicationBattery, midYComplicationBattery, 5, p);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String text = mAmbient
                    ? String.format("%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%02d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));

            //setTextSizeForWidth(mTextPaint, canvas.getWidth() * 0.9f, text);
            Rect textBounds = new Rect();
            mTextPaint.getTextBounds("00:00:00", 0, 8, textBounds);
            canvas.drawText(text, (canvas.getWidth() - textBounds.width()) /2, mYOffset, mTextPaint);
        }

        private void setTextSizeForWidth(Paint paint, float desiredWidth,
                                                String text) {

            // Pick a reasonably large value for the test. Larger values produce
            // more accurate results, but may cause problems with hardware
            // acceleration. But there are workarounds for that, too; refer to
            // http://stackoverflow.com/questions/6253528/font-size-too-large-to-fit-in-cache
            final float testTextSize = 48f;

            // Get the bounds of the text, using our testTextSize.
            paint.setTextSize(testTextSize);
            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);

            // Calculate the desired size as a proportion of our testTextSize.
            float desiredTextSize = testTextSize * desiredWidth / bounds.width();

            // Set the paint for that size.
            paint.setTextSize(desiredTextSize);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
