package edu.umd.hcil.impressionistpainter434;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.text.MessageFormat;
import java.util.Random;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView _imageView;

    private Canvas _offScreenCanvas = null;
    private Bitmap _offScreenBitmap = null;
    private Paint _paint = new Paint();

    private int _alpha = 150;
    private int _defaultRadius = 75; // Size of most brushes
    private Paint _paintBorder = new Paint();
    private BrushType _brushType = BrushType.Circle;
    private float _maxBrushRadius = 200; // Used for the dynamic circle brush
    private VelocityTracker velocityTracker = null; // Used for the dynamic circle brush
    private String textBrushValue = "TextBrush"; // Used for the custom text brush


    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        _paint.setColor(Color.RED);
        _paint.setAlpha(_alpha);
        _paint.setAntiAlias(true);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setStrokeWidth(4);
        _paint.setTextSize(100);
        _paint.setTextAlign(Paint.Align.CENTER);

        _paintBorder.setColor(Color.BLACK);
        _paintBorder.setStrokeWidth(3);
        _paintBorder.setStyle(Paint.Style.STROKE);
        _paintBorder.setAlpha(50);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            _offScreenCanvas = new Canvas(_offScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        _imageView = imageView;
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        _brushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){
        _offScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true); // Just resets the bitmap and canvas
        _offScreenCanvas = new Canvas(_offScreenBitmap);
        invalidate(); // Updates the view
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(_offScreenBitmap != null) {
            canvas.drawBitmap(_offScreenBitmap, 0, 0, _paint);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(_imageView), _paintBorder);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){
        float curTouchX = motionEvent.getX();
        float curTouchY = motionEvent.getY();

        switch(motionEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                if (velocityTracker ==  null) { // Set the VelocityTracker for the dynamic circle brush
                    velocityTracker = VelocityTracker.obtain();
                } else velocityTracker.clear();
                velocityTracker.addMovement(motionEvent); // Sets current movement for tracker, used below
                break;
            case MotionEvent.ACTION_MOVE:
                Bitmap bitmap = ((BitmapDrawable)_imageView.getDrawable()).getBitmap();
                int imageOffsetX = 0; // Used in repositioning drawing canvas that is often offcenter (knnown bug).
                int imageOffsetY =  (this.getHeight() - bitmap.getHeight())/2;
                int historySize = motionEvent.getHistorySize();
                for (int i = 0; i < historySize; i++) {
                    float touchX = motionEvent.getHistoricalX(i);
                    float touchY = motionEvent.getHistoricalY(i);
                    if (touchX >= imageOffsetX && touchX < bitmap.getWidth() + imageOffsetX &&
                            touchY >= imageOffsetY && touchY < bitmap.getHeight() + imageOffsetY) {
                        _paint.setColor(bitmap.getPixel((int) touchX - imageOffsetX,
                                (int) touchY - imageOffsetY));
                        _paint.setAlpha(_alpha);
                        velocityTracker.addMovement(motionEvent);

                        // Draw based on current brush
                        if (_brushType == BrushType.Circle) {
                            _offScreenCanvas.drawCircle(touchX, touchY, _defaultRadius, _paint);
                        } else if (_brushType == BrushType.DynamicCircle) {
                            velocityTracker.computeCurrentVelocity(1000, 20000);
                            // Calculate distance moved between tracked positions
                            float xVelo = velocityTracker.getXVelocity();
                            float yVelo = velocityTracker.getYVelocity();
                            // (dx^2 + dy^2)^(1/2) = distance
                            float totalVelo = (float) Math.pow((Math.pow(xVelo, 2.0F)
                                    + Math.pow(yVelo, 2.0F)), 0.5F);
                            _offScreenCanvas.drawCircle(touchX, touchY,
                                    totalVelo*_maxBrushRadius/20000, _paint);
                        } else if (_brushType == BrushType.Square) {
                            _offScreenCanvas.drawRect(touchX - _defaultRadius, touchY - _defaultRadius,
                                    touchX + _defaultRadius, touchY + _defaultRadius, _paint);
                        } else if (_brushType == BrushType.Line) {
                            // Drawing below duplicated to give the line more thickness
                            _offScreenCanvas.drawLine(touchX - _defaultRadius, touchY - _defaultRadius,
                                    touchX + _defaultRadius, touchY + _defaultRadius, _paint);
                            _offScreenCanvas.drawLine(touchX - _defaultRadius, touchY - _defaultRadius - 1,
                                    touchX + _defaultRadius, touchY + _defaultRadius - 1, _paint);
                            _offScreenCanvas.drawLine(touchX - _defaultRadius, touchY - _defaultRadius + 1,
                                    touchX + _defaultRadius, touchY + _defaultRadius + 1, _paint);
                        } else if (_brushType == BrushType.CMSC434Text) {
                            _offScreenCanvas.drawText("CMSC434", touchX, touchY + 50, _paint);
                        } else if (_brushType == BrushType.CustomText) {
                            _offScreenCanvas.drawText(textBrushValue, touchX, touchY + 50, _paint);
                        }
                    }
                }
                if (curTouchX >= imageOffsetX && curTouchX < bitmap.getWidth() + imageOffsetX &&
                        curTouchY >= imageOffsetY && curTouchY < bitmap.getHeight() + imageOffsetY) {
                    _paint.setColor(bitmap.getPixel((int) curTouchX - imageOffsetX,
                            (int) curTouchY - imageOffsetY));
                    _paint.setAlpha(_alpha);
                    velocityTracker.addMovement(motionEvent);

                    // Draw based on current brush
                    if (_brushType == BrushType.Circle) {
                        _offScreenCanvas.drawCircle(curTouchX, curTouchY, _defaultRadius, _paint);
                    } else if (_brushType == BrushType.DynamicCircle) {
                        velocityTracker.computeCurrentVelocity(1000, 20000);
                        // Calculate distance moved between tracked positions
                        float xVelo = velocityTracker.getXVelocity();
                        float yVelo = velocityTracker.getYVelocity();
                        // (dx^2 + dy^2)^(1/2) = distance
                        float totalVelo = (float) Math.pow((Math.pow(xVelo, 2.0F)
                                + Math.pow(yVelo, 2.0F)), 0.5F);
                        _offScreenCanvas.drawCircle(curTouchX, curTouchY,
                                totalVelo*_maxBrushRadius/20000, _paint);
                    } else if (_brushType == BrushType.Square) {
                        _offScreenCanvas.drawRect(curTouchX - _defaultRadius, curTouchY - _defaultRadius,
                                curTouchX + _defaultRadius, curTouchY + _defaultRadius, _paint);
                    } else if (_brushType == BrushType.Line) {
                        // Drawing below duplicated to give the line more thickness
                        _offScreenCanvas.drawLine(curTouchX - _defaultRadius, curTouchY - _defaultRadius,
                                curTouchX + _defaultRadius, curTouchY + _defaultRadius, _paint);
                        _offScreenCanvas.drawLine(curTouchX - _defaultRadius, curTouchY - _defaultRadius - 1,
                                curTouchX + _defaultRadius, curTouchY + _defaultRadius - 1, _paint);
                        _offScreenCanvas.drawLine(curTouchX - _defaultRadius, curTouchY - _defaultRadius + 1,
                                curTouchX + _defaultRadius, curTouchY + _defaultRadius + 1, _paint);
                    } else if (_brushType == BrushType.CMSC434Text) {
                        _offScreenCanvas.drawText("CMSC434", curTouchX, curTouchY + 50, _paint);
                    } else if (_brushType == BrushType.CustomText) {
                        _offScreenCanvas.drawText(textBrushValue, curTouchX, curTouchY + 50, _paint);
                    }
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                break;
        }

        return true;
    }




    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }

    public void setTextBrushValue(String text) {
        this.textBrushValue = text;
    }

    // Save function taken from this answer online:
    // http://stackoverflow.com/questions/7887078/android-saving-file-to-external-storage/7887114#7887114
    public String saveImage() {
        Bitmap finalBitmap = _offScreenBitmap;
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/impressionist_drawings");
        myDir.mkdirs();
        Random generator = new Random();
        int n = 10000;
        n = generator.nextInt(n);
        String fname = "Image-"+ n +".jpg";
        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Function to make image appear in gallery from this answer online:
    // http://stackoverflow.com/questions/20859584/how-to-save-image-in-android-gallery
    // (This function is used in the save onClick handler in the MainActivity)
    public static void addImageToGallery(final String filePath, final Context context) {

        ContentValues values = new ContentValues();

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.MediaColumns.DATA, filePath);

        context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }
}


