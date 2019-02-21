package eu.malek.utils;

import android.graphics.Rect;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;


public class UtilsViewGroup {

    public static final int DELAY = 100;
    private static final String TAG = UtilsViewGroup.class.toString();

    /**
     * Recursively enables / disables ViewGroup children by
     * {@link View#setEnabled(boolean)} , {@link View#setFocusable(boolean)} ...and on
     *
     * @param enable
     * @param vg
     */
    public static void setChildrenEnabled(final boolean enable, ViewGroup vg) {
        operateOnViewGroup(vg, new SetEnabledOperation(enable));
    }

    public static void setChildrenEnabled(final boolean enable, @NonNull ViewGroup vg,
                                          @NonNull Collection<View> skippedViews) {
        operateOnViewGroup(vg, new SetEnabledOperation(enable), skippedViews);
    }

    public static void setChildrenEnabled(final boolean enable, @NonNull ViewGroup vg,
                                          @NonNull Collection<View> skippedViews,
                                          @NonNull Collection<ViewGroup> skippedViewGroups) {
        operateOnViewGroup(vg, new SetEnabledOperation(enable), skippedViews, skippedViewGroups);
    }

    public static void setChildrenEnabled(final boolean enable, @NonNull ViewGroup vg,
                                          @NonNull View[] skippedViews) {
        operateOnViewGroup(vg, new SetEnabledOperation(enable), Arrays.asList(skippedViews));
    }

    public static void set1stChildrenEnabled(final boolean enable, @NonNull ViewGroup vg,
                                             @NonNull View[] skippedViews) {
        operateOnChildViewGroup(vg, new SetEnabledOperation(enable), Arrays.asList(skippedViews));
    }


    public static void setChildrenEnabled(final boolean enable, @NonNull ViewGroup vg,
                                          @NonNull View[] skippedViews,
                                          @NonNull ViewGroup[] skippedViewGroups) {
        operateOnViewGroup(vg, new SetEnabledOperation(enable), Arrays.asList(skippedViews),
                Arrays.asList(skippedViewGroups));
    }


    public static void clearEditable(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof Editable) {
                ((Editable) child).clear();
            }
            if (child instanceof ViewGroup) {
                clearEditable((ViewGroup) child);
            }
        }
    }

    public static void clearViews(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof EditText) {
                ((EditText) child).setText("");
            }
            else if (child instanceof Checkable) {
                ((Checkable) child).setChecked(false);
            }

            if (child instanceof ViewGroup) {
                clearViews((ViewGroup) child);
            }
        }
    }

    public static void setCheckableChecked(final boolean checked, ViewGroup vg) {

        operateOnViewGroup(vg, new Operation() {
            @Override
            public void operate(View child) {
                if (child instanceof Checkable) {
                    ((Checkable) child).setChecked(checked);
                }
            }
        });
    }


    public static void operateOnViewGroup(@NonNull ViewGroup vg, @NonNull Operation operation) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);

            operation.operate(child);

            if (child instanceof ViewGroup) {
                operateOnViewGroup((ViewGroup) child, operation);
            }
        }
    }


    public static void operateOnViewGroup(@NonNull ViewGroup vg, @NonNull Operation operation,
                                          @NonNull Collection<View> skippedViews, @NonNull
                                          Collection<ViewGroup>
                                                  skippedViewGroups) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (!skippedViews.contains(child)) {
                operation.operate(child);
            }
            if (child instanceof ViewGroup && !skippedViewGroups.contains(child)) {
                operateOnViewGroup((ViewGroup) child, operation);
            }
        }
    }


    public static void operateOnViewGroup(@NonNull ViewGroup vg, @NonNull Operation operation,
                                          @NonNull Collection<View> skippedViews) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (!skippedViews.contains(child)) {
                operation.operate(child);
            }
            if (child instanceof ViewGroup) {
                operateOnViewGroup((ViewGroup) child, operation);
            }
        }
    }

    /**
     * Operates only on direct child views
     *
     * @param vg
     * @param operation
     * @param skippedViews
     */
    public static void operateOnChildViewGroup(@NonNull ViewGroup vg, @NonNull Operation operation,
                                               @NonNull Collection<View> skippedViews) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (!skippedViews.contains(child)) {
                operation.operate(child);
            }
        }
    }

    public static void setVisibile(ViewGroup vg, final boolean visible, View[] ignored) {
        operateOnViewGroup((ViewGroup) vg, new Operation
                () {
            @Override
            public void operate(View child) {
                child.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }, Arrays.asList(ignored));
    }

    public static void setEnabledLowerViews(ViewGroup vg, View view, final boolean enabled,
                                            Collection<ViewGroup> skippedViewGroups) {
        final int targetY = UtilsView.ViewGetYOnScreenLocation(view);
        operateOnViewGroup(vg, new Operation() {
            @Override
            public void operate(View child) {
                int Y = UtilsView.ViewGetYOnScreenLocation(child);
                if (Y > targetY) {
                    child.setEnabled(enabled);
                    child.setFocusable(enabled);

                }
            }
        }, new ArrayList<View>(), skippedViewGroups);
    }

    /**
     * Will scroll the {@code scrollView} to make {@code viewToScroll} visible.
     *
     * @param scrollView        parent of {@code scrollableContent}
     * @param scrollableContent a child of {@code scrollView} whitch holds the scrollable content (fills the viewport).
     * @param viewToScroll      a child of {@code scrollableContent} to whitch will scroll the the {@code scrollView}.
     *                          Beware!!:Sometimes works probably must be direct child of {@code scrollableContent}
     */
    public static void scrollToView(final ScrollView scrollView, final ViewGroup scrollableContent,
                                    final View viewToScroll) {
        long delay = DELAY; //delay to let finish with possible modifications to ScrollView
        scrollView.postDelayed(new Runnable() {
            public void run() {
                Rect viewToScrollRect = new Rect(); //coordinates to scroll to
                viewToScroll.getHitRect(
                        viewToScrollRect); //fills viewToScrollRect with coordinates of viewToScroll relative to its
                // parent (LinearLayout)
                scrollView.requestChildRectangleOnScreen(scrollableContent, viewToScrollRect,
                        false); //ScrollView will make sure, the given viewToScrollRect is visible
            }
        }, delay);
    }


    /**
     * Posts delayed Scrolls to view which can be descendant child of {@link NestedScrollView} or {@link ScrollView}
     * @param viewToScroll
     */
    public static void scrollToView(final View viewToScroll) {
        long delay = DELAY;
        viewToScroll.postDelayed(new Runnable() {
            public void run() {
                if (viewToScroll != null) {
                    scrollToViewInLayout(viewToScroll);
                }
            }
        }, delay);
    }

    /**
     * Scrolls to view which can be descendant child of {@link NestedScrollView} or {@link ScrollView}
     * @param view
     */
    public static void scrollToViewInLayout(View view) {
        //From Stack Overflow
        int vt = view.getTop();
        int vb = view.getBottom();
        View v = view;
        for (; ; ) {
            ViewParent vp = v.getParent();
            if (vp == null || !(vp instanceof ViewGroup)) {
                break;
            }
            ViewGroup parent = (ViewGroup) vp;


            boolean isScrollView;
            boolean isNestedScrollView = false;

            if ((isScrollView = parent instanceof ScrollView) ||
                    (isNestedScrollView = parent instanceof NestedScrollView)) {
                ViewGroup sv = parent;
                // Code based on ScrollView.computeScrollDeltaToGetChildRectOnScreen(Rect rect) (Android v5.1.1):
                int height = sv.getHeight();
                int screenTop = sv.getScrollY();
                int screenBottom = screenTop + height;
                int fadingEdge = sv.getVerticalFadingEdgeLength();
                // leave room for top fading edge as long as rect isn't at very top
                if (vt > 0) {
                    screenTop += fadingEdge;
                }
                // leave room for bottom fading edge as long as rect isn't at very bottom
                if (vb < sv.getChildAt(0).getHeight()) {
                    screenBottom -= fadingEdge;
                }
                int scrollYDelta = 0;

                if (vb > screenBottom && vt > screenTop) {
                    // need to move down to get it in view: move down just enough so
                    // that the entire rectangle is in view (or at least the first
                    // screen size chunk).

                    if (vb - vt > height) // just enough to get screen size chunk on
                    {
                        scrollYDelta += (vt - screenTop);
                    }
                    else              // get entire rect at bottom of screen
                    {
                        scrollYDelta += (vb - screenBottom);
                    }

                    // make sure we aren't scrolling beyond the end of our content
                    int bottom = sv.getChildAt(0).getBottom();
                    int distanceToBottom = bottom - screenBottom;
                    scrollYDelta = Math.min(scrollYDelta, distanceToBottom);
                }
                else if (vt < screenTop && vb < screenBottom) {
                    // need to move up to get it in view: move up just enough so that
                    // entire rectangle is in view (or at least the first screen
                    // size chunk of it).

                    if (vb - vt > height)    // screen size chunk
                    {
                        scrollYDelta -= (screenBottom - vb);
                    }
                    else                  // entire rect at top
                    {
                        scrollYDelta -= (screenTop - vt);
                    }

                    // make sure we aren't scrolling any further than the top our content
                    scrollYDelta = Math.max(scrollYDelta, -sv.getScrollY());
                }

                if (isScrollView){
                    ((ScrollView) sv).smoothScrollBy(0, scrollYDelta);
                }else if (isNestedScrollView){
                    ((NestedScrollView) sv).smoothScrollBy(0, scrollYDelta);
                }
                break;
            }
            // Transform coordinates to parent:
            int dy = parent.getTop() - parent.getScrollY();
            vt += dy;
            vb += dy;

            v = parent;
        }
    }


    public static void scrollAndRequestFocus(@NonNull View toScrollAndFocus) {
        scrollToView(toScrollAndFocus);
        toScrollAndFocus.requestFocus();
    }

    public static void ScrollToViewAndFocus(View topNonvalidView) {
        scrollToView(topNonvalidView);
        topNonvalidView.requestFocus();
    }

    @NonNull
    public static <T> ArrayList<T> findByType(ViewGroup view,final Class<T> tClass) {
        final ArrayList<T> foundObjcts = new ArrayList<>();

        operateOnViewGroup(view, new Operation() {
            @Override
            public void operate(View child) {
                if ( tClass.isInstance(child) ){
                    foundObjcts.add(((T) child));
                }
            }
        });

        return foundObjcts;
    }

    public interface Operation {
        public void operate(View child);
    }

    private static class SetEnabledOperation implements Operation {
        private final boolean enable;

        public SetEnabledOperation(boolean enable) {
            this.enable = enable;
        }

        @Override
        public void operate(View child) {
            child.setEnabled(enable);
            child.setFocusable(enable);
            child.setFocusableInTouchMode(enable);
        }
    }
}
