package com.rethoughtsolutions.swipecardentry.sample;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.Calendar;
import java.util.regex.Pattern;

/**
 * Implementation of a credit card entry field, with validation of number, and expiry month/year.
 */
public class TestSwipeCardEntry extends View {

    /**
     * Listener of JSCardEntry, notified when the card entry has been completed/in progress.
     */
    public interface Listener {

        /**
         * Called whenever the state of this card entry has gone to/from completed.
         *
         * @param completed true if the card entry has been completed.
         */
        public void onCardEntryCompleted(boolean completed);
    }

    private static final String NUMBER_HINT = "1234 5678 9012 3456";

    private static final String EXPIRY_HINT = "MM/YY";

    private static final String CVC_HINT = "CVC";

    private static final String AMEX_CVC_HINT = "4DBC";

    private static final int IMAGE_BUFFER_PADDING = 25;

    private static final int BLINK_DURATION = 500;

    private enum Mode {
        NUMBER,
        EXPIRYMONTH,
        EXPIRYYEAR,
        CVC
    }

    private enum CardType {
        UNKNOWN("", "", 16, R.drawable.generic_bank, R.drawable.generic_bank, new int[]{},
                CVC_HINT),
        VISA("^4[0-9]$", "^4[0-9]{6,}$", 16, R.drawable.visa_curved, R.drawable.cvv_visa,
                new int[]{4, 8, 12}, CVC_HINT),
        MASTERCARD("^5[1-5]$", "^5[1-5][0-9]{5,}$", 16, R.drawable.mastercard_curved,
                R.drawable.cvv_mc, new int[]{4, 8, 12}, CVC_HINT),
        AMEX("^3[47]$", "^3[47][0-9]{5,}$", 15, R.drawable.american_express_curved,
                R.drawable.cvv_amex, new int[]{4, 10}, AMEX_CVC_HINT);

        private Pattern mPartial;

        private Pattern mWhole;

        private int mResource;

        private int mCVCResource;

        private int mLength;

        private int mCVCLength;

        private int mBreaks[];

        private String mCVCHint;

        CardType(String guess, String whole, int length, int resource, int cvvResource,
                int[] breaks, String cvcHint) {
            mPartial = Pattern.compile(guess);
            mWhole = Pattern.compile(whole);
            mLength = length;
            mResource = resource;
            mCVCResource = cvvResource;
            mBreaks = breaks;
            mCVCLength = cvcHint.length();
            mCVCHint = cvcHint;
        }

        boolean guess(CharSequence match) {
            return mPartial.matcher(match).matches();
        }

        boolean validate(CharSequence match) {
            if (mWhole.matcher(match).matches()) {
                return validateNumber(match);
            }
            return false;
        }

        boolean isCorrectLength(int length) {
            return mLength == length;
        }

        boolean hasBreakAt(int index) {
            for (int num : mBreaks) {
                if (num == index) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Validates the credit card number using the luhn algorithm, returns true if valid.
         *
         * @param number the number to validate.
         * @return true if valid.
         */
        boolean validateNumber(CharSequence number) {
            int sum = 0;
            final int size = number.length();
            final int checkDigit = number.charAt(size - 1) - '0';

            boolean doubleDigit = true;

            for (int index = size - 1; --index >= 0; doubleDigit = !doubleDigit) {
                int digit = number.charAt(index) - '0';

                if (doubleDigit) {
                    digit *= 2;

                    if (digit > 9) {
                        //sum the two digits together, the first is always 1 as the heighest number is 18
                        digit = 1 + (digit % 10);
                    }
                }
                sum += digit;
            }

            return ((sum + checkDigit) % 10) == 0;
        }
    }

    private Listener mListener;

    private CardType mCardType;

    private Mode mMode;

    private boolean mCompleted = false;

    private boolean mError = false;

    private float mExpiryOffset = 0.0f;

    private float mCVCOffset = 0.0f;

    private Bitmap mBitmap;

    private Bitmap mCVCBitmap;

    private ValueAnimator mAnimator;

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mCursorOn = false;

    private TextPaint mTextPaint;

    private TextPaint mHintPaint;

    private TextPaint mErrorPaint;

    private int mTextOffsetY = 0;

    private int mTextGapWidth = 0;


    private Runnable mBlink = new Runnable() {
        @Override
        public void run() {
            mCursorOn = !mCursorOn;
            postInvalidate();
            mHandler.postDelayed(this, BLINK_DURATION);
        }
    };

    private Editable mNumber;

    private Editable mNumberFormatted;

    private Editable mMonth;

    private Editable mYear;

    private Editable mExpiryFormatted;

    private Editable mCVC;

    private Paint mBitmapPaint;

    private boolean mTouchDown = false;

    public TestSwipeCardEntry(Context context) {
        super(context);
        initialize(context, null, 0);
    }

    public TestSwipeCardEntry(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs, android.R.style.Widget_EditText);
    }

    public TestSwipeCardEntry(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int verticalPadding = getPaddingTop() + getPaddingBottom();
        int horizontalPadding = getPaddingLeft() + getPaddingRight();

        int textHeight = (int) (mTextPaint.descent() - mTextPaint.ascent());
        int imageHeight = mBitmap.getHeight();

        int height = Math.max(textHeight, imageHeight);
        mTextOffsetY = (height - textHeight) / 2;
        height += verticalPadding;

        int minimumTextWidth = (int) mTextPaint.measureText(NUMBER_HINT);

        int width = mBitmap.getWidth() + (2 * IMAGE_BUFFER_PADDING) + minimumTextWidth
                + horizontalPadding;

        setMeasuredDimension(resolveSizeAndState(width, widthMeasureSpec, 0),
                resolveSizeAndState(height, heightMeasureSpec, 0));
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        BaseInputConnection inputConnection = new BaseInputConnection(this, true);
        outAttrs.inputType = InputType.TYPE_CLASS_NUMBER;
        return inputConnection;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDown = true;
                return true;
            case MotionEvent.ACTION_UP:
                if (mTouchDown) {
                    mTouchDown = false;

                    requestFocus();
                    // show the keyboard so we can enter text
                    InputMethodManager imm = (InputMethodManager) getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(this, 0);

                    performClick();

                    return true;
                }

        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        int paddingLeft = getPaddingLeft();
        float xPos = paddingLeft + mBitmap.getWidth() + (2 * IMAGE_BUFFER_PADDING);
        canvas.clipRect(xPos, 0, getWidth(), getHeight());
        float offsetX = (Float) mAnimator.getAnimatedValue();
        xPos += offsetX;

        int baseline = getPaddingTop() + mTextOffsetY - (int) mTextPaint.ascent();

        int length = mNumberFormatted.length();
        if (length == 0) {
            canvas.drawText(NUMBER_HINT, xPos, baseline, mHintPaint);
        } else {
            if ((mError) && (mMode == Mode.NUMBER)) {
                canvas.drawText(mNumberFormatted, 0, length, xPos, baseline, mErrorPaint);
            } else {
                canvas.drawText(mNumberFormatted, 0, length, xPos, baseline, mTextPaint);
            }
        }

        if (mMode != Mode.NUMBER) {
            //we draw the Expiry
            if (mExpiryFormatted.length() == 0) {
                canvas.drawText(EXPIRY_HINT, mExpiryOffset + offsetX, baseline, mHintPaint);
                canvas.drawText(mCardType.mCVCHint, mCVCOffset + offsetX, baseline, mHintPaint);
            } else {
                if ((mError) && ((mMode == Mode.EXPIRYYEAR) || (mMode == Mode.EXPIRYMONTH))) {
                    canvas.drawText(mExpiryFormatted, 0, mExpiryFormatted.length(),
                            mExpiryOffset + offsetX, baseline, mErrorPaint);
                } else {
                    canvas.drawText(mExpiryFormatted, 0, mExpiryFormatted.length(),
                            mExpiryOffset + offsetX, baseline, mTextPaint);
                }

                if (mCVC.length() == 0) {
                    canvas.drawText(mCardType.mCVCHint, mCVCOffset + offsetX, baseline, mHintPaint);
                } else {
                    canvas.drawText(mCVC, 0, mCVC.length(), mCVCOffset + offsetX, baseline,
                            mTextPaint);
                }
            }
        }

        canvas.restore();
        if (mMode == Mode.CVC) {
            float yPos = (getMeasuredHeight() - mCVCBitmap.getHeight()) / 2;
            canvas.drawBitmap(mCVCBitmap, IMAGE_BUFFER_PADDING + paddingLeft, yPos, mBitmapPaint);
        } else {
            float yPos = (getMeasuredHeight() - mBitmap.getHeight()) / 2;
            canvas.drawBitmap(mBitmap, IMAGE_BUFFER_PADDING + paddingLeft, yPos, mBitmapPaint);
        }

        if (mCursorOn) {
            float cursorPosition;
            switch (mMode) {
                case NUMBER:
                    cursorPosition = mTextPaint
                            .measureText(mNumberFormatted, 0, mNumberFormatted.length());
                    cursorPosition += xPos;
                    break;
                case EXPIRYMONTH:
                case EXPIRYYEAR:
                    cursorPosition = mTextPaint
                            .measureText(mExpiryFormatted, 0, mExpiryFormatted.length());
                    cursorPosition += mExpiryOffset + offsetX;
                    break;
                default: //CVC
                    cursorPosition = mTextPaint.measureText(mCVC, 0, mCVC.length());
                    cursorPosition += mCVCOffset + offsetX;
            }

            Paint.FontMetrics metrics = mTextPaint.getFontMetrics();
            canvas.drawRect(cursorPosition, baseline + metrics.top, cursorPosition + 1,
                    baseline + metrics.descent, mTextPaint);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) {
            return;
        }

        InputMethodManager inputMethodManager = (InputMethodManager) getContext()
                .getSystemService(
                        Context.INPUT_METHOD_SERVICE);

        if (!enabled) {
            if (inputMethodManager != null && inputMethodManager.isActive(this)) {
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }

        super.setEnabled(enabled);

        if (enabled) {
            if (inputMethodManager != null) {
                inputMethodManager.restartInput(this);
            }
            startBlinking();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && hasFocus()) {
            startBlinking();
        } else if (!hasWindowFocus) {
            stopBlinking();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) {
            startBlinking();
        } else {
            stopBlinking();
        }
    }

    /**
     * Replaces the current listener (if any) with the given listener.
     *
     * @param listener the new listener (or null).
     * @return the old listener.
     */
    public Listener setListener(Listener listener) {
        Listener oldListener = mListener;
        mListener = listener;
        return oldListener;
    }

    /**
     * Clears CVC, Expiry and Number fields and sets the number to the given parameter. If this is
     * valid then we the state will move to expiry month to fill in the rest of the details.
     *
     * @param number the number to fill in. (Not null)
     */
    public void setNumber(String number) {
        mNumber.clear();
        mMonth.clear();
        mYear.clear();
        mCVC.clear();
        mNumberFormatted.clear();
        mExpiryFormatted.clear();

        mCardType = CardType.UNKNOWN;
        mMode = Mode.NUMBER;
        mNumber.append(number);

        mNumberFormatted.append(number);

        if (mNumber.length() >= 2) {
            setCardType(guessCardType(mNumber.subSequence(0,2)));
        }

        for (int index = mCardType.mBreaks.length; --index >= 0; ) {
            int space = mCardType.mBreaks[index];
            if (mNumberFormatted.length() > space) {
                mNumberFormatted.insert(space, " ");
            }
        }

        if (mCardType.isCorrectLength(mNumber.length())) {
            validateNumber();
        }

        checkIsCompleted();

        postInvalidate();
    }

    /**
     * @return true if the card entry has been completed (and valid), false otherwise.
     */
    public boolean isCompleted() {
        return mCompleted;
    }

    /**
     * @return the card number, this may or may not be valid, check with isCompleted.
     */
    public String getNumber() {
        return mNumber.toString();
    }

    /**
     * @return the CVC, this may or may not be valid, check with isCompleted.
     */
    public String getCVC() {
        return mCVC.toString();
    }

    /**
     * @return the expiry month (1 >= expiry month >= 12), or 0 if not set.
     */
    public int getExpiryMonth() {
        if (mMode.ordinal() > Mode.EXPIRYMONTH.ordinal()) {
            return Integer.parseInt(mMonth.toString());
        } else {
            return 0;
        }
    }

    /**
     * @return the last 2 digits of the expiry year (so 2018 would be 18), or 0 if not set.
     */
    public int getExpiryYear() {
        if (mMode.ordinal() > Mode.EXPIRYYEAR.ordinal()) {
            return Integer.parseInt(mYear.toString());
        } else {
            return 0;
        }
    }

    private void initialize(Context context, AttributeSet attrs, int defStyle) {
        if (isInEditMode()) {
            return;
        }
        setFocusableInTouchMode(true);
        setFocusable(true);

        ColorStateList textColor = null;
        ColorStateList hintColor = null;
        int errorColor = Color.RED;
        int textSize = SpToPixels(context,
                15); //context.getTheme().applyStyle().getTextAppearance.SM;

        if (attrs != null) {
            TypedArray attributes = context
                    .obtainStyledAttributes(attrs, R.styleable.TestSwipeCardEntry, defStyle, 0);
            textColor = attributes
                    .getColorStateList(R.styleable.TestSwipeCardEntry_android_textColor);
            textSize = attributes
                    .getDimensionPixelSize(R.styleable.TestSwipeCardEntry_android_textSize,
                            textSize);
            hintColor = attributes.getColorStateList(
                    R.styleable.TestSwipeCardEntry_android_textColorHint);
            attributes.recycle();
        }

        if (textColor == null) {
            textColor = ColorStateList.valueOf(0xFF000000);
        }

        if (hintColor == null) {
            textColor = ColorStateList.valueOf(0xFF7F7F7F);
        }

        setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Perform action on key press
                    processKeyEvent(event.getKeyCode());
                }
                return true;
            }
        });

        final Resources resources = getResources();

        mTextPaint = new TextPaint();
        mTextPaint.setTextSize(textSize);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setColor(textColor.getDefaultColor());

        mHintPaint = new TextPaint(mTextPaint);
        mHintPaint.setColor(hintColor.getDefaultColor());

        mErrorPaint = new TextPaint(mTextPaint);
        mErrorPaint.setColor(errorColor);

        mNumber = new SpannableStringBuilder();
        mMonth = new SpannableStringBuilder();
        mYear = new SpannableStringBuilder();
        mCVC = new SpannableStringBuilder();
        mNumberFormatted = new SpannableStringBuilder();
        mExpiryFormatted = new SpannableStringBuilder();

        mAnimator = ValueAnimator.ofFloat(0.0f, 0.0f);
        mAnimator.setDuration(500);
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                postInvalidate();
            }
        });

        mBitmapPaint = new Paint();
        mBitmapPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        setCardType(CardType.UNKNOWN);
        mMode = Mode.NUMBER;
    }


    private void processKeyEvent(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            clearFocus();
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            startBlinking();
            switch (mMode) {
                case CVC:
                    if (mCVC.length() > 0) {
                        removeLastChar(mCVC);
                        break;
                    } else {
                        //we are going back to number mode:
                        mMode = Mode.EXPIRYYEAR;
                    }
                case EXPIRYYEAR:
                    if (mYear.length() > 0) {
                        removeLastChar(mYear);
                        removeLastChar(mExpiryFormatted);
                        break;
                    } else {
                        //we are going back to number mode:
                        removeLastChar(mExpiryFormatted);
                        mMode = Mode.EXPIRYMONTH;
                    }
                case EXPIRYMONTH:
                    if (mMonth.length() > 0) {
                        removeLastChar(mMonth);
                        removeLastChar(mExpiryFormatted);
                        break;
                    } else {
                        //we are going back to number mode:
                        mMode = Mode.NUMBER;
                        mAnimator.reverse();
                    }
                case NUMBER:
                    if (mNumber.length() > 0) {
                        removeLastChar(mNumber);
                        removeLastChar(mNumberFormatted);
                        if (mCardType.hasBreakAt(mNumber.length() + 1)) {
                            removeLastChar(mNumberFormatted);
                        }

                        if (mNumber.length() < 2) {
                            setCardType(CardType.UNKNOWN);
                        }
                    }
                    break;
            }
            mError = false;
            checkIsCompleted();
        } else if (!mError) {
            startBlinking();
            int number = keyCode - KeyEvent.KEYCODE_0;
            if ((number >= 0) && (number <= 9)) {
                String numberAsString = Integer.toString(number);
                switch (mMode) {
                    case NUMBER:
                        mNumber.append(numberAsString);
                        mNumberFormatted.append(numberAsString);
                        int length = mNumber.length();
                        if (mCardType.hasBreakAt(length)) {
                            mNumberFormatted.append(' ');
                        }
                        if (length < 2) {
                            //do nothing
                        } else if (length == 2) {
                            setCardType(guessCardType(mNumber));
                        } else if (mCardType.isCorrectLength(length)) {
                            validateNumber();
                        }
                        break;
                    case EXPIRYMONTH:
                        if (mMonth.length() == 0) {
                            if ((number == 0) || (number == 1)) {
                                mMonth.append(numberAsString);
                                mExpiryFormatted.append(numberAsString);
                            }
                        } else if (mMonth.length() == 1) {
                            if (mMonth.charAt(0) == '1') {
                                if ((number == 0) || (number == 1) || (number == 2)) {
                                    mMonth.append(numberAsString);
                                    mExpiryFormatted.append(numberAsString);
                                }
                            } else if (number != 0) {
                                mMonth.append(numberAsString);
                                mExpiryFormatted.append(numberAsString);
                            }

                            if (mMonth.length() == 2) {
                                int month = Integer.parseInt(mMonth.toString());
                                if ((month >= 1) && (month <= 12)) {
                                    mExpiryFormatted.append('/');
                                    mMode = Mode.EXPIRYYEAR;
                                }
                            }

                        }
                        break;
                    case EXPIRYYEAR:
                        final int yearLength = mYear.length();

                        if (yearLength == 0) {
                            if (number >= 1) {
                                mYear.append(numberAsString);
                                mExpiryFormatted.append(numberAsString);
                            }
                        } else if (yearLength == 1) {
                            mYear.append(numberAsString);

                            Calendar calendar = Calendar.getInstance();
                            int actualYear = calendar.get(Calendar.YEAR);
                            int enteredYear = 2000 + Integer.parseInt(mYear.toString());

                            if (actualYear == enteredYear) {
                                //validate the month
                                int actualMonth = calendar.get(Calendar.MONTH) + 1;
                                int enteredMonth = Integer.parseInt(mMonth.toString());
                                if (actualMonth <= enteredMonth) {
                                    mExpiryFormatted.append(numberAsString);
                                    mMode = Mode.CVC;
                                } else {
                                    removeLastChar(mYear);
                                }
                            } else if (actualYear < enteredYear) {
                                mExpiryFormatted.append(numberAsString);
                                mMode = Mode.CVC;
                            } else {
                                removeLastChar(mYear);
                            }
                        }
                        break;
                    case CVC:
                        if (mCVC.length() < mCardType.mCVCLength) {
                            mCVC.append(numberAsString);
                            checkIsCompleted();
                        }
                        break;
                }
            }
        }

        postInvalidate();
    }

    private CardType guessCardType(CharSequence cardType) {
        for (CardType type : CardType.values()) {
            if (type.guess(cardType)) {
                return type;
            }
        }

        return CardType.UNKNOWN;
    }

    private void setCardType(CardType type) {
        if (mCardType != type) {
            mCardType = type;
            if (type != null) {
                mBitmap = BitmapFactory.decodeResource(getResources(), type.mResource);
                mCVCBitmap = BitmapFactory.decodeResource(getResources(), type.mCVCResource);
            }
        }
    }

    private void removeLastChar(Editable editable) {
        int length = editable.length() - 1;
        editable.delete(length, length + 1);
    }

    private void startBlinking() {
        mHandler.removeCallbacks(mBlink);
        mHandler.postDelayed(mBlink, BLINK_DURATION);
        mCursorOn = true;
        postInvalidate();
    }

    private void stopBlinking() {
        mHandler.removeCallbacks(mBlink);
        mCursorOn = false;
        postInvalidate();
    }

    private void validateNumber() {
        if (mCardType.validateNumber(mNumber)) {
            mMode = Mode.EXPIRYMONTH;

            //calculate the animation and animate
            final int numberLength = mCardType.mLength;
            final int lastBreakIndex = mCardType.mBreaks[mCardType.mBreaks.length -1];
            final int fourNumberTextWidth = (int) mTextPaint
                    .measureText(mNumber, lastBreakIndex, numberLength);
            final int expiryTextWidth = (int) mTextPaint.measureText(EXPIRY_HINT);
            final int cvcTextWidth = (int) mTextPaint.measureText(mCardType.mCVCHint);
            final int formattedNumberWidth = (int) mTextPaint.measureText(mNumberFormatted, 0,
                    mNumberFormatted.length());

            final int paddingLeft = getPaddingLeft();
            final int imageEndPosition = paddingLeft + mBitmap.getWidth() + (2 * IMAGE_BUFFER_PADDING);
            final int twelveNumberTextWidth = formattedNumberWidth - fourNumberTextWidth;
            final int wholeWidth = (getMeasuredWidth() + twelveNumberTextWidth) - (paddingLeft + getPaddingRight());
            mCVCOffset = wholeWidth - cvcTextWidth;

            final int leftPosition = (imageEndPosition + formattedNumberWidth);
            mExpiryOffset = (((mCVCOffset - leftPosition) - expiryTextWidth) / 2) + leftPosition;

            mAnimator.setFloatValues(0.0f, 0 - twelveNumberTextWidth);

            mAnimator.start();
        } else {
            mError = true;
        }
    }

    private void checkIsCompleted() {
        boolean completed = (mMode == Mode.CVC) && (mCVC.length() == mCardType.mCVCLength);
        if (completed != mCompleted) {
            mCompleted = completed;
            if (mListener != null) {
                mListener.onCardEntryCompleted(mCompleted);
            }
        }
    }

    private static int SpToPixels(Context context, int sp) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return Math.round(scaledDensity * sp);
    }
}
