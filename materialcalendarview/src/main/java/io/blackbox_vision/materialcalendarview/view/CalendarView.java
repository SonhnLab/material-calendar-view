package io.blackbox_vision.materialcalendarview.view;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.Scroller;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.blackbox_vision.materialcalendarview.R;
import io.blackbox_vision.materialcalendarview.internal.data.Day;
import io.blackbox_vision.materialcalendarview.internal.utils.CalendarUtils;

import static io.blackbox_vision.materialcalendarview.internal.utils.ScreenUtils.getScreenHeight;


/**
 * CalendarView class
 *
 * @author jonatan.salas
 */
public final class CalendarView extends LinearLayout {
    private static final Interpolator DEFAULT_ANIM_INTERPOLATOR = new DecelerateInterpolator(3.0f);
    private static final long DEFAULT_ANIM_DURATION = 1500;

    private static final String KEY_STATE = "superState";
    private static final String KEY_MONTH_INDEX = "currentMonthIndex";

    private static final int SUNDAY = 1;
    private static final int MONDAY = 2;
    private static final int TUESDAY = 4;
    private static final int WEDNESDAY = 8;
    private static final int THURSDAY = 16;
    private static final int FRIDAY = 32;
    private static final int SATURDAY = 64;

    private static final int[] FLAGS = new int[]{
            SUNDAY,
            MONDAY,
            TUESDAY,
            WEDNESDAY,
            THURSDAY,
            FRIDAY,
            SATURDAY
    };

    private static final int[] WEEK_DAYS = new int[]{
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
    };

    /**
     * Indicates that the CalendarView is in an idle, settled state. The current page
     * is fully in view and no animation is in progress.
     */
    int SCROLL_STATE_IDLE = 0;

    /**
     * Indicates that the CalendarView is currently being dragged by the user.
     */
    int SCROLL_STATE_DRAGGING = 1;

    /**
     * Indicates that the CalendarView is in the process of settling to a final position.
     */
    int SCROLL_STATE_SETTLING = 2;

    boolean USE_CACHE = false;
    int MIN_DISTANCE_FOR_FLING = 25; // dips
    int DEFAULT_GUTTER_SIZE = 16; // dips
    int MIN_FLING_VELOCITY = 400; // dips

    /**
     * Sentinel value for no current active pointer.
     */
    int INVALID_POINTER = -1;

    // If the CalendarView is at least this close to its final position, complete the scroll
    // on touch down and let the user interact with the content inside instead of
    // "catching" the flinging Calendar.
    int CLOSE_ENOUGH = 2; // dp

    private boolean scrollingCacheEnabled;
    private boolean isBeingDragged;
    private boolean isUnableToDrag;
    private int defaultGutterSize;
    private int touchSlop;

    /**
     * Position of the last motion event.
     */
    private float lastMotionX;
    private float lastMotionY;
    private float initialMotionX;
    private float initialMotionY;

    private Scroller scroller;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int activePointerId = INVALID_POINTER;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker velocityTracker;
    private int minimumVelocity;
    private int maximumVelocity;
    private int flingDistance;
    private int closeEnough;

    private int scrollState = SCROLL_STATE_IDLE;

    private final Runnable endScrollRunnable = () -> setScrollState(SCROLL_STATE_IDLE);

    private GestureDetectorCompat gestureDetector;

    @Nullable
    private OnMonthTitleClickListener onMonthTitleClickListener;

    @Nullable
    private OnDateClickListener onDateClickListener;

    @Nullable
    private OnDateLongClickListener onDateLongClickListener;

    @Nullable
    private OnMonthChangeListener onMonthChangeListener;

    @Nullable
    private OnMultipleDaySelectedListener onMultipleDaySelectedListener;

    private Calendar calendar;
    private Date lastSelectedDay;

    private Typeface typeface;
    private int disabledDayBackgroundColor;
    private int disabledDayTextColor;
    private int calendarBackgroundColor;
    private int selectedDayBackgroundColor;
    private int weekBackgroundColor;
    private int titleBackgroundColor;
    private int selectedDayTextColor;
    private int titleTextColor;
    private int dayOfWeekTextColor;
    private int currentDayTextColor;
    private int weekendTextColor;
    private int weekendDays;
    private int buttonBackgroundColor;
    private int currentDayBackgroundColor;
    private int backButtonDrawable;
    private int nextButtonDrawable;
    private boolean isOverflowDateVisible;
    private boolean isMultiSelectDayEnabled;

    private int firstDayOfWeek;
    private int currentMonthIndex;
    private Map<Integer, List<Date>> selectedDatesForMonth = new HashMap<>();

    // Day of weekendDays
    private int[] totalDayOfWeekend;

    // true for ordinary day, false for a weekendDays.
    private boolean isCommonDay;

    private View view;
    private HeaderView headerView;
    private DatePickerDialog pickerDialog;

    @Nullable
    private Date disabledDate;

    /**
     * Constructor with arguments. It receives a
     * Context used to get the resources.
     *
     * @param context - the context used to get the resources.
     */
    public CalendarView(Context context) {
        this(context, null);
    }

    /**
     * Constructor with arguments. It receives a
     * Context used to get the resources.
     *
     * @param context - the context used to get the resources.
     * @param attrs   - attribute set with custom styles.
     */
    public CalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTouchVariables();
        takeStyles(attrs);
        drawCalendar();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final Bundle stateToSave = new Bundle();

        stateToSave.putParcelable(KEY_STATE, superState);
        stateToSave.putInt(KEY_MONTH_INDEX, currentMonthIndex);

        return stateToSave;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedInstanceState = (Bundle) state;

            state = savedInstanceState.getParcelable(KEY_STATE);
            currentMonthIndex = savedInstanceState.getInt(KEY_MONTH_INDEX);

            update(Calendar.getInstance(Locale.getDefault()));
        }

        super.onRestoreInstanceState(state);
    }

    private void initTouchVariables() {
        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        final float density = getContext().getResources().getDisplayMetrics().density;

        gestureDetector = new GestureDetectorCompat(getContext(), new CalendarGestureDetector());
        scroller = new Scroller(getContext(), null);

        touchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
        minimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        flingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
        closeEnough = (int) (CLOSE_ENOUGH * density);
        defaultGutterSize = (int) (DEFAULT_GUTTER_SIZE * density);
    }

    /***
     * Method that gets and set the attributes of the CalendarView class.
     *
     * @param attrs - Attribute set object with custom values to be setted
     */
    private void takeStyles(AttributeSet attrs) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.MaterialCalendarView, 0, 0);

        final int white = ContextCompat.getColor(getContext(), android.R.color.white);
        final int black = ContextCompat.getColor(getContext(), android.R.color.black);
        final int dayDisableBackground = ContextCompat.getColor(getContext(), R.color.day_disabled_background_color);
        final int dayDisableTextColor = ContextCompat.getColor(getContext(), R.color.day_disabled_text_color);
        final int daySelectedBackground = ContextCompat.getColor(getContext(), R.color.selected_day_background);
        final int dayCurrent = ContextCompat.getColor(getContext(), R.color.current_day_of_month);
        final int endColor = ContextCompat.getColor(getContext(), R.color.weekend_color);

        try {
            buttonBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarButtonBackgroundColor, black);
            calendarBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarBackgroundColor, white);
            titleBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarTitleBackgroundColor, white);
            titleTextColor = a.getColor(R.styleable.MaterialCalendarView_calendarTitleTextColor, white);
            weekBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarWeekBackgroundColor, white);
            dayOfWeekTextColor = a.getColor(R.styleable.MaterialCalendarView_calendarDayOfWeekTextColor, black);
            disabledDayBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarDisabledDayBackgroundColor, dayDisableBackground);
            disabledDayTextColor = a.getColor(R.styleable.MaterialCalendarView_calendarDisabledDayTextColor, dayDisableTextColor);
            selectedDayBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarSelectedDayBackgroundColor, daySelectedBackground);
            selectedDayTextColor = a.getColor(R.styleable.MaterialCalendarView_calendarSelectedDayTextColor, white);
            currentDayBackgroundColor = a.getColor(R.styleable.MaterialCalendarView_calendarCurrentDayBackgroundColor, dayCurrent);
            currentDayTextColor = a.getColor(R.styleable.MaterialCalendarView_calendarCurrentDayTextColor, dayCurrent);
            weekendTextColor = a.getColor(R.styleable.MaterialCalendarView_calendarWeekendTextColor, endColor);
            weekendDays = a.getInteger(R.styleable.MaterialCalendarView_calendarWeekendDays, 0);
            isOverflowDateVisible = a.getBoolean(R.styleable.MaterialCalendarView_calendarIsOverflowDatesVisible, true);
            isMultiSelectDayEnabled = a.getBoolean(R.styleable.MaterialCalendarView_calendarIsMultiSelectDayEnabled, false);
            backButtonDrawable = a.getResourceId(R.styleable.MaterialCalendarView_calendarBackButtonDrawable, R.drawable.ic_keyboard_arrow_left_black_24dp);
            nextButtonDrawable = a.getResourceId(R.styleable.MaterialCalendarView_calendarBackButtonDrawable, R.drawable.ic_keyboard_arrow_right_black_24dp);
        } finally {
            if (null != a) {
                a.recycle();
            }
        }
    }

    private void drawCalendar() {
        view = LayoutInflater.from(getContext()).inflate(R.layout.material_calendar_view, this, true);
        calendar = Calendar.getInstance(Locale.getDefault());
        firstDayOfWeek = Calendar.SUNDAY;
        currentMonthIndex = 0;

        update(Calendar.getInstance(Locale.getDefault()));
    }

    private void drawHeaderView() {
        headerView = (HeaderView) view.findViewById(R.id.header_view);

        headerView.setBackgroundColor(titleBackgroundColor);

        headerView.setTitle(CalendarUtils.getDateTitle(Locale.getDefault(), currentMonthIndex))
                .setNextButtonDrawable(nextButtonDrawable)
                .setBackButtonDrawable(backButtonDrawable)
                .setNextButtonColor(buttonBackgroundColor)
                .setBackButtonColor(buttonBackgroundColor)
                .setTitleColor(titleTextColor)
                .setTypeface(typeface)
                .setOnTitleClickListener(this::onTitleClick)
                .setOnNextButtonClickListener(this::onNextButtonClick)
                .setOnBackButtonClickListener(this::onBackButtonClick);
    }

    public void onTitleClick() {
        showDatePickerDialog();

        if (onMonthTitleClickListener != null) {
            onMonthTitleClickListener.onMonthTitleClick(calendar.getTime());
        }
    }

    public void onNextButtonClick(@NonNull View v) {
        currentMonthIndex++;
        updateCalendarOnTouch();
    }

    public void onBackButtonClick(@NonNull View v) {
        currentMonthIndex--;
        updateCalendarOnTouch();
    }

    private void updateCalendarOnTouch() {
        headerView.setTitle(CalendarUtils.getDateTitle(Locale.getDefault(), currentMonthIndex));

        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.add(Calendar.MONTH, currentMonthIndex);

        update(calendar);

        if (onMonthChangeListener != null) {
            onMonthChangeListener.onMonthChange(calendar.getTime());
        }
    }

    private void drawWeekView() {
        final List<String> shortWeekDays = CalendarUtils.getShortWeekDays(Locale.getDefault());
        final View v = view.findViewById(R.id.week_layout);

        v.setBackgroundColor(weekBackgroundColor);

        TextView textView;
        String day;

        for (int i = 1; i < shortWeekDays.size(); i++) {
            day = shortWeekDays.get(i);
            day = day.substring(0, day.length() < 3 ? day.length() : 3).toUpperCase();

            textView = (TextView) v.findViewWithTag(getContext().getString(R.string.day_of_week) + CalendarUtils.calculateWeekIndex(calendar, i));
            textView.setText(day);

            isCommonDay = true;

            if (totalDayOfWeekend.length != 0) {
                for (int weekend : totalDayOfWeekend) {
                    if (i == weekend) {
                        textView.setTextColor(weekendTextColor);
                        isCommonDay = false;
                    }
                }
            }

            if (isCommonDay) {
                textView.setTextColor(dayOfWeekTextColor);
            }

            if (null != typeface) {
                textView.setTypeface(typeface);
            }
        }
    }

    /**
     * Date Picker (Month & Year only)
     *
     * @author chris.chen
     */
    //TODO review it, we need to provide a Material Design version of Picker for Month and Year
    private void showDatePickerDialog() {
        calendar = Calendar.getInstance(Locale.getDefault());

        final int year = calendar.get(Calendar.YEAR);
        final int month = calendar.get(Calendar.MONTH);
        final int day = calendar.get(Calendar.DAY_OF_MONTH);

        pickerDialog = new DatePickerDialog(getContext(), R.style.CalendarViewTitle, this::onDateSet, year, month, day);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setSpinnerVisibility("day", View.GONE);
            setSpinnerVisibility("month", View.VISIBLE);
            setSpinnerVisibility("year", View.VISIBLE);
        } else {
            //Older SDK versions
            final Field f[] = pickerDialog.getDatePicker().getClass().getDeclaredFields();

            for (Field field : f) {
                setSpinnerVisibility(field, "mDayPicker|mDaySpinner", View.GONE);
                setSpinnerVisibility(field, "mMonthPicker|mMonthSpinner", View.VISIBLE);
                setSpinnerVisibility(field, "mYearPicker|mYearSpinner", View.VISIBLE);
            }
        }

        pickerDialog.show();
    }

    private void setSpinnerVisibility(String key, int visibility) {
        final int spinnerId = Resources.getSystem().getIdentifier(key, "id", "android");

        if (spinnerId != 0) {
            final View spinner = pickerDialog.getDatePicker().findViewById(spinnerId);

            if (null != spinner) {
                spinner.setVisibility(visibility);
            }
        }

    }

    private void setSpinnerVisibility(Field field, String attr, int visibility) {
        final String[] attrs = attr.split("|");

        if (field.getName().equals(attrs[0]) || field.getName().equals(attrs[1])) {
            field.setAccessible(true);
            View pickerView = null;

            try {
                pickerView = (View) field.get(pickerDialog.getDatePicker());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            if (null != pickerView) {
                pickerView.setVisibility(visibility);
            }
        }
    }

    public void onDateSet(DatePicker datePicker, int year, int monthOfYear, int dayOfMonth) {
        final int y = calendar.get(Calendar.YEAR);
        final int m = calendar.get(Calendar.MONTH);

        currentMonthIndex = (year - y) * 12 + (monthOfYear - m);

        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.add(Calendar.MONTH, currentMonthIndex);

        update(calendar);

        if (onMonthChangeListener != null) {
            onMonthChangeListener.onMonthChange(calendar.getTime());
        }
    }

    private void drawAdapterView() {
        final List<Day> days = CalendarUtils.obtainDays(calendar, currentMonthIndex);

        DayView textView;
        ViewGroup container;

        Day disabledDay = new Day();

        if (disabledDate != null) {
            Calendar disabledCalendar = Calendar.getInstance();
            disabledCalendar.setTime(disabledDate);

            disabledDay.setDay(disabledCalendar.get(Calendar.DAY_OF_MONTH))
                    .setMonth(disabledCalendar.get(Calendar.MONTH))
                    .setYear(disabledCalendar.get(Calendar.YEAR));
        } else
            disabledDay.setDay(-121); //random number for check

        for (int i = 0; i < days.size(); i++) {
            Day day = days.get(i);

            int fixedIndex = i + 1;

            container = (ViewGroup) view.findViewWithTag(getContext().getString(R.string.day_of_month_container) + fixedIndex);
            textView = (DayView) view.findViewWithTag(getContext().getString(R.string.day_of_month_text) + fixedIndex);

            container.setOnClickListener(null);

            if (null != typeface) {
                textView.setTypeface(typeface);
            }

            textView.setDay(day);

            if (day.isCurrentMonth()) {
                textView.setVisibility(View.VISIBLE);

                container.setOnClickListener(this::onClick);
                container.setOnLongClickListener(this::onLongClick);

                textView.setBackgroundColor(calendarBackgroundColor);

                isCommonDay = true;

                if (totalDayOfWeekend.length != 0) {
                    final Calendar calendar = day.toCalendar(Locale.getDefault());

                    for (int weekend : totalDayOfWeekend) {
                        if (weekend == calendar.get(Calendar.DAY_OF_WEEK)) {
                            textView.setTextColor(weekendTextColor);
                            isCommonDay = false;
                        }
                    }
                }

                if (isCommonDay) {
                    textView.setTextColor(dayOfWeekTextColor);
                }

                if (day.isCurrentDay()) {
                    drawCurrentDay(new Date(System.currentTimeMillis()));
                }

                if (disabledDay.getDay() != -121) {
                    if (day.compareTo(disabledDay) < 1) {
                        textView.setTextColor(disabledDayTextColor);
                        textView.setBackgroundColor(calendarBackgroundColor);
                    }
                }

            } else {
                if (!isOverflowDateVisible) {
                    textView.setVisibility(View.INVISIBLE);
                } else {
                    textView.setVisibility(View.VISIBLE);
                    textView.setEnabled(false);

                    textView.setBackgroundColor(disabledDayBackgroundColor);
                    textView.setTextColor(disabledDayTextColor);
                }
            }
        }
    }

    private void clearDayViewSelection(Date currentDate) {
        if (currentDate != null) {
            Calendar calendar = Calendar.getInstance(Locale.getDefault());
            calendar.setFirstDayOfWeek(firstDayOfWeek);
            calendar.setTime(currentDate);

            DayView dayView = findViewByCalendar(calendar);
            dayView.setBackgroundColor(calendarBackgroundColor);
            isCommonDay = true;

            if (totalDayOfWeekend.length != 0) {
                for (int weekend : totalDayOfWeekend) {
                    if (weekend == calendar.get(Calendar.DAY_OF_WEEK)) {
                        dayView.setTextColor(weekendTextColor);
                        isCommonDay = false;
                    }
                }
            }

            if (isCommonDay) {
                dayView.setTextColor(dayOfWeekTextColor);
            }
        }
    }

    public DayView findViewByDate(@NonNull Date dateToFind) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTime(dateToFind);
        return (DayView) getView(getContext().getString(R.string.day_of_month_text), calendar);
    }

    private DayView findViewByCalendar(@NonNull Calendar calendarToFind) {
        return (DayView) getView(getContext().getString(R.string.day_of_month_text), calendarToFind);
    }

    private int getDayIndexByDate(Calendar calendar) {
        int monthOffset = CalendarUtils.getMonthOffset(calendar, firstDayOfWeek);
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        return currentDay + monthOffset;
    }

    private View getView(String key, Calendar currentCalendar) {
        final int index = getDayIndexByDate(currentCalendar);
        return view.findViewWithTag(key + index);
    }

    public void update(Calendar c) {
        calendar = c;
        calendar.setFirstDayOfWeek(firstDayOfWeek);

        calculateWeekEnds();

        drawHeaderView();
        drawWeekView();
        drawAdapterView();
    }

    private void calculateWeekEnds() {
        totalDayOfWeekend = new int[2];
        int weekendIndex = 0;

        for (int i = 0; i < FLAGS.length; i++) {
            boolean isContained = containsFlag(this.weekendDays, FLAGS[i]);

            if (isContained) {
                totalDayOfWeekend[weekendIndex] = WEEK_DAYS[i];
                weekendIndex++;
            }
        }
    }

    private boolean containsFlag(int flagSet, int flag) {
        return (flagSet | flag) == flagSet;
    }

    private void drawCurrentDay(@NonNull Date todayDate) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTime(todayDate);

        if (CalendarUtils.isToday(calendar)) {
            final DayView dayOfMonth = findViewByCalendar(calendar);

            dayOfMonth.setTextColor(currentDayTextColor);

            Drawable d = ContextCompat.getDrawable(getContext(), R.drawable.circular_background);
            d.setColorFilter(currentDayBackgroundColor, PorterDuff.Mode.SRC_ATOP);

            ViewCompat.setBackground(dayOfMonth, d);
        }
    }

    public void markDateAsSelected(Date currentDate) {
        Calendar currentCalendar = Calendar.getInstance(Locale.getDefault());
        currentCalendar.setFirstDayOfWeek(firstDayOfWeek);
        currentCalendar.setTime(currentDate);

        // Clear previous marks
        if (!isMultiSelectDayEnabled) {
            clearDayViewSelection(new Date(System.currentTimeMillis()));
            clearDayViewSelection(lastSelectedDay);
        } else {
            int month = currentCalendar.get(Calendar.MONTH);
            List<Date> dates = selectedDatesForMonth.get(month);

            if (null != dates) {
                dates.add(lastSelectedDay);
            } else {
                dates = new ArrayList<>();
                dates.add(lastSelectedDay);
            }

            selectedDatesForMonth.put(month, dates);

            if (null != onMultipleDaySelectedListener) {
                onMultipleDaySelectedListener.onMultipleDaySelected(month, dates);
            }
        }

        // Store current values as last values
        setLastSelectedDay(currentDate);

        // Mark current day as selected
        DayView view = findViewByCalendar(currentCalendar);

        Drawable d = ContextCompat.getDrawable(getContext(), R.drawable.circular_background);
        d.setColorFilter(selectedDayBackgroundColor, PorterDuff.Mode.SRC_ATOP);

        ViewCompat.setBackground(view, d);
        view.setTextColor(selectedDayTextColor);
    }

    public boolean onLongClick(View view) {
        // Extract day selected
        ViewGroup dayOfMonthContainer = (ViewGroup) view;
        String tagId = (String) dayOfMonthContainer.getTag();
        tagId = tagId.substring(getContext().getString(R.string.day_of_month_container).length(), tagId.length());
        final TextView dayOfMonthText = (TextView) view.findViewWithTag(getContext().getString(R.string.day_of_month_text) + tagId);

        // Fire event
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(firstDayOfWeek);
        calendar.setTime(calendar.getTime());
        calendar.set(Calendar.DAY_OF_MONTH, Integer.valueOf(dayOfMonthText.getText().toString()));

        markDateAsSelected(calendar.getTime());

        //Set the current day color
        drawCurrentDay(calendar.getTime());

        if (onDateLongClickListener != null) {
            onDateLongClickListener.onDateLongClick(calendar.getTime());
        }

        return false;
    }

    public void onClick(View view) {
        // Extract day selected
        ViewGroup dayOfMonthContainer = (ViewGroup) view;
        String tagId = (String) dayOfMonthContainer.getTag();
        tagId = tagId.substring(getContext().getString(R.string.day_of_month_container).length(), tagId.length());

        final TextView dayOfMonthText = (TextView) view.findViewWithTag(getContext().getString(R.string.day_of_month_text) + tagId);

        // Fire event
        Calendar c = Calendar.getInstance();

        c.setFirstDayOfWeek(firstDayOfWeek);
        c.setTime(calendar.getTime());
        c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(dayOfMonthText.getText().toString()));

        //Set the current day color
        drawCurrentDay(c.getTime());

        if (onDateClickListener != null) {
            if (disabledDate != null) {
                if (c.getTime().compareTo(disabledDate) > 0) {
                    markDateAsSelected(c.getTime());
                    onDateClickListener.onDateClick(c.getTime());
                }
            } else {
                markDateAsSelected(c.getTime());
                onDateClickListener.onDateClick(c.getTime());
            }
        }
    }

    private boolean isGutterDrag(float x, float dx) {
        return (x < defaultGutterSize && dx > 0) || (x > getWidth() - defaultGutterSize && dx < 0);
    }

    private void setScrollingCacheEnabled(boolean enabled) {
        if (scrollingCacheEnabled != enabled) {
            scrollingCacheEnabled = enabled;

            if (USE_CACHE) {
                final int size = getChildCount();
                for (int i = 0; i < size; ++i) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() != GONE) {
                        child.setDrawingCacheEnabled(enabled);
                    }
                }
            }
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == activePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            lastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
            activePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);

            if (velocityTracker != null) {
                velocityTracker.clear();
            }
        }
    }

    private void setScrollState(int newState) {
        if (scrollState == newState) {
            return;
        }

        scrollState = newState;
    }

    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final ViewParent parent = getParent();

        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    /**
     * Tests scroll ability within child views of v given a delta of dx.
     *
     * @param v      View to test for horizontal scroll ability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx     Delta scrolled in pixels
     * @param x      X coordinate of the active touch point
     * @param y      Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }

    private void completeScroll(boolean postEvents) {
        boolean needPopulate = scrollState == SCROLL_STATE_SETTLING;
        if (needPopulate) {
            // Done with scroll, no longer want to cache view drawing.
            setScrollingCacheEnabled(false);
            scroller.abortAnimation();
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = scroller.getCurrX();
            int y = scroller.getCurrY();
            if (oldX != x || oldY != y) {
                scrollTo(x, y);
            }
        }

        if (needPopulate) {
            if (postEvents) {
                ViewCompat.postOnAnimation(this, endScrollRunnable);
            } else {
                endScrollRunnable.run();
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (null != gestureDetector) {
            gestureDetector.onTouchEvent(ev);
            super.dispatchTouchEvent(ev);
            return true;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        // Always take care of the touch gesture being complete.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // Release the drag.
            isBeingDragged = false;
            isUnableToDrag = false;
            activePointerId = INVALID_POINTER;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            return false;
        }

        // Nothing more to do here if we have decided whether or not we
        // are dragging.
        if (action != MotionEvent.ACTION_DOWN) {
            if (isBeingDragged) {
                return true;
            }

            if (isUnableToDrag) {
                return false;
            }
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * isBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. lastMotionY is set to the y value
                * of the down event.
                */
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float dx = x - lastMotionX;
                final float xDiff = Math.abs(dx);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = Math.abs(y - initialMotionY);

                if (dx != 0 && !isGutterDrag(lastMotionX, dx) &&
                        canScroll(this, false, (int) dx, (int) x, (int) y)) {
                    // Nested view has scrollable area under this point. Let it be handled there.
                    lastMotionX = x;
                    lastMotionY = y;
                    isUnableToDrag = true;
                    return false;
                }
                if (xDiff > touchSlop && xDiff * 0.5f > yDiff) {
                    isBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                    setScrollState(SCROLL_STATE_DRAGGING);
                    lastMotionX = dx > 0 ? initialMotionX + touchSlop :
                            initialMotionX - touchSlop;
                    lastMotionY = y;
                    setScrollingCacheEnabled(true);
                } else if (yDiff > touchSlop) {
                    // The finger has moved enough in the vertical
                    // direction to be counted as a drag...  abort
                    // any attempt to drag horizontally, to work correctly
                    // with children that have scrolling containers.
                    isUnableToDrag = true;
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                lastMotionX = initialMotionX = ev.getX();
                lastMotionY = initialMotionY = ev.getY();
                activePointerId = MotionEventCompat.getPointerId(ev, 0);
                isUnableToDrag = false;

                scroller.computeScrollOffset();
                if (scrollState == SCROLL_STATE_SETTLING &&
                        Math.abs(scroller.getFinalX() - scroller.getCurrX()) > closeEnough) {
                    isBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                    setScrollState(SCROLL_STATE_DRAGGING);
                } else {
                    completeScroll(false);
                    isBeingDragged = false;
                }
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }

        velocityTracker.addMovement(ev);

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return isBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    /**
     * CalendarGestureDetector class used to detect Swipes gestures.
     *
     * @author jonatan.salas
     */
    public final class CalendarGestureDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > touchSlop && Math.abs(velocityX) > minimumVelocity && Math.abs(velocityX) < maximumVelocity) {
                        if (e2.getX() - e1.getX() > flingDistance) {
                            currentMonthIndex--;
                            updateCalendarOnTouch();

                        } else if (e1.getX() - e2.getX() > flingDistance) {
                            currentMonthIndex++;
                            updateCalendarOnTouch();
                        }
                    }
                }

                return true;

            } catch (Exception ex) {
                ex.printStackTrace();
            }

            return super.onFling(e1, e2, velocityX, velocityY);
        }
    }

    /**
     * Interface that define a method to
     * implement to handle a selected date event,
     *
     * @author jonatan.salas
     */
    public interface OnDateClickListener {

        /**
         * Method that lets you handle
         * when a user touches a particular date.
         *
         * @param selectedDate - the date selected by the user.
         */
        void onDateClick(@NonNull Date selectedDate);
    }

    /**
     * Interface that define a method to
     * implement to handle a selected date event,
     *
     * @author jonatan.salas
     */
    public interface OnDateLongClickListener {

        /**
         * Method that lets you handle
         * when a user touches a particular date.
         *
         * @param selectedDate - the date selected by the user.
         */
        void onDateLongClick(@NonNull Date selectedDate);
    }

    /**
     * Interface that define a method to implement to handle
     * a month changed event.
     *
     * @author jonatan.salas
     */
    public interface OnMonthChangeListener {

        /**
         * Method that lets you handle when a goes to back or next
         * month.
         *
         * @param monthDate - the date with the current month
         */
        void onMonthChange(@NonNull Date monthDate);
    }

    /**
     * Interface that define a method to implement to handle
     * a month title change event.
     *
     * @author chris.chen
     */
    public interface OnMonthTitleClickListener {

        void onMonthTitleClick(@NonNull Date monthDate);
    }

    public interface OnMultipleDaySelectedListener {

        void onMultipleDaySelected(int month, @NonNull List<Date> dates);
    }

    public CalendarView shouldAnimateOnEnter(boolean shouldAnimate) {
        shouldAnimateOnEnter(shouldAnimate, DEFAULT_ANIM_DURATION, DEFAULT_ANIM_INTERPOLATOR);
        return this;
    }

    public CalendarView shouldAnimateOnEnter(boolean shouldAnimate, long duration) {
        shouldAnimateOnEnter(shouldAnimate, duration, DEFAULT_ANIM_INTERPOLATOR);
        return this;
    }

    public CalendarView shouldAnimateOnEnter(boolean shouldAnimate, @NonNull Interpolator interpolator) {
        shouldAnimateOnEnter(shouldAnimate, DEFAULT_ANIM_DURATION, interpolator);
        return this;
    }

    public CalendarView shouldAnimateOnEnter(boolean shouldAnimate, long duration, @NonNull Interpolator interpolator) {
        if (shouldAnimate) {
            ViewCompat.setTranslationY(this, getScreenHeight(getContext()));
            ViewCompat.setAlpha(this, 0f);
            ViewCompat.animate(this)
                    .translationY(0f)
                    .setDuration(duration)
                    .alpha(1f)
                    .setInterpolator(interpolator)
                    .start();

            invalidate();
        }

        return this;
    }

    public CalendarView setOnMonthTitleClickListener(@Nullable OnMonthTitleClickListener onMonthTitleClickListener) {
        this.onMonthTitleClickListener = onMonthTitleClickListener;
        invalidate();
        return this;
    }

    public CalendarView setOnDateClickListener(@Nullable OnDateClickListener onDateClickListener) {
        this.onDateClickListener = onDateClickListener;
        invalidate();
        return this;
    }

    public CalendarView setOnDateLongClickListener(@Nullable OnDateLongClickListener onDateLongClickListener) {
        this.onDateLongClickListener = onDateLongClickListener;
        invalidate();
        return this;
    }

    public CalendarView setOnMonthChangeListener(@Nullable OnMonthChangeListener onMonthChangeListener) {
        this.onMonthChangeListener = onMonthChangeListener;
        invalidate();
        return this;
    }

    public CalendarView setOnMultipleDaySelectedListener(@Nullable OnMultipleDaySelectedListener onMultipleDaySelectedListener) {
        this.onMultipleDaySelectedListener = onMultipleDaySelectedListener;
        invalidate();
        return this;
    }

    private CalendarView setLastSelectedDay(Date lastSelectedDay) {
        this.lastSelectedDay = lastSelectedDay;
        invalidate();
        return this;
    }

    public CalendarView setTypeface(Typeface typeface) {
        this.typeface = typeface;
        invalidate();
        return this;
    }

    public CalendarView setIsOverflowDateVisible(boolean isOverflowDateVisible) {
        this.isOverflowDateVisible = isOverflowDateVisible;
        invalidate();
        return this;
    }

    public CalendarView setFirstDayOfWeek(int firstDayOfWeek) {
        this.firstDayOfWeek = firstDayOfWeek;
        invalidate();
        return this;
    }

    public void setDisabledDate(Date disabledDate) {
        this.disabledDate = disabledDate;
    }

    public CalendarView setDisabledDayBackgroundColor(int disabledDayBackgroundColor) {
        this.disabledDayBackgroundColor = disabledDayBackgroundColor;
        invalidate();
        return this;
    }

    public CalendarView setDisabledDayTextColor(int disabledDayTextColor) {
        this.disabledDayTextColor = disabledDayTextColor;
        invalidate();
        return this;
    }

    public CalendarView setCalendarBackgroundColor(int calendarBackgroundColor) {
        this.calendarBackgroundColor = calendarBackgroundColor;
        invalidate();
        return this;
    }

    public CalendarView setSelectedDayBackgroundColor(int selectedDayBackgroundColor) {
        this.selectedDayBackgroundColor = selectedDayBackgroundColor;
        invalidate();
        return this;
    }

    public CalendarView setWeekBackgroundColor(int weekBackgroundColor) {
        this.weekBackgroundColor = weekBackgroundColor;
        invalidate();
        return this;
    }

    public CalendarView setTitleBackgroundColor(int titleBackgroundColor) {
        this.titleBackgroundColor = titleBackgroundColor;
        invalidate();
        return this;
    }

    public CalendarView setSelectedDayTextColor(int selectedDayTextColor) {
        this.selectedDayTextColor = selectedDayTextColor;
        invalidate();
        return this;
    }

    public CalendarView setTitleTextColor(int titleTextColor) {
        this.titleTextColor = titleTextColor;
        invalidate();
        return this;
    }

    public CalendarView setDayOfWeekTextColor(int dayOfWeekTextColor) {
        this.dayOfWeekTextColor = dayOfWeekTextColor;
        invalidate();
        return this;
    }

    public CalendarView setCurrentDayTextColor(int currentDayTextColor) {
        this.currentDayTextColor = currentDayTextColor;
        invalidate();
        return this;
    }

    public CalendarView setWeekendTextColor(int weekendTextColor) {
        this.weekendTextColor = weekendTextColor;
        invalidate();
        return this;
    }

    public CalendarView setWeekendDays(int weekendDays) {
        this.weekendDays = weekendDays;
        invalidate();
        return this;
    }

    public CalendarView setButtonBackgroundColor(int buttonBackgroundColor) {
        this.buttonBackgroundColor = buttonBackgroundColor;
        setNextButtonColor(buttonBackgroundColor);
        setBackButtonColor(buttonBackgroundColor);
        invalidate();
        return this;
    }

    public CalendarView setBackButtonColor(@ColorRes int colorId) {
        this.headerView.setBackButtonColor(ContextCompat.getColor(getContext(), colorId));
        invalidate();
        return this;
    }

    public CalendarView setNextButtonColor(@ColorRes int colorId) {
        this.headerView.setNextButtonColor(ContextCompat.getColor(getContext(), colorId));
        invalidate();
        return this;
    }

    public CalendarView setCurrentDayBackgroundColor(int currentDayBackgroundColor) {
        this.currentDayBackgroundColor = currentDayBackgroundColor;
        invalidate();
        return this;
    }

    public CalendarView setBackButtonDrawable(@DrawableRes int drawableId) {
        this.headerView.setBackButtonDrawable(drawableId);
        invalidate();
        return this;
    }

    public CalendarView setNextButtonDrawable(@DrawableRes int drawableId) {
        this.headerView.setNextButtonDrawable(drawableId);
        invalidate();
        return this;
    }

    public CalendarView setMultiSelectDayEnabled(boolean multiSelectDayEnabled) {
        isMultiSelectDayEnabled = multiSelectDayEnabled;
        invalidate();
        return this;
    }

    public boolean isOverflowDateVisible() {
        return isOverflowDateVisible;
    }

    public boolean isMultiSelectDayEnabled() {
        return isMultiSelectDayEnabled;
    }
}
