package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Selection;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

public final class DialogUtils {

    public interface TextSetListener {
        void onTextSet(String text);
    }

    public interface TextAndSpinnerSetListener {
        void onTextAndSpinnerSet(String text, int spinnerIndex);
    }

    private static void setDialogLayoutPadding(Activity activity, LinearLayout layout) {
        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics());
        // https://www.google.com/design/spec/components/dialogs.html#dialogs-specs
        int paddingTopAndSides = Math.round(16 * dipInPixels);
        int paddingBottom = Math.round(24 * dipInPixels);

        layout.setPadding(paddingTopAndSides, paddingTopAndSides, paddingTopAndSides, paddingBottom);
    }

    public static void textInput(Activity activity, int titleText, String initialText,
                                 int positiveButtonText, final TextSetListener onPositive,
                                 int neutralButtonText, final TextSetListener onNeutral,
                                 int negativeButtonText, final TextSetListener onNegative,
                                 final DialogInterface.OnDismissListener onDismiss) {
        final EditText input = new EditText(activity);
        input.setSingleLine();
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        input.setImeActionLabel(activity.getResources().getString(positiveButtonText), KeyEvent.KEYCODE_ENTER);
        input.setOnEditorActionListener((v, actionId, event) -> {
            onPositive.onTextSet(input.getText().toString());
            dialogHolder[0].dismiss();
            return true;
        });

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setDialogLayoutPadding(activity, layout);
        layout.addView(input);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setTitle(titleText).setView(layout)
            .setPositiveButton(positiveButtonText, (d, whichButton) -> onPositive.onTextSet(input.getText().toString()));

        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText, (dialog, which) -> onNeutral.onTextSet(input.getText().toString()));
        }

        if (onNegative == null) {
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setNegativeButton(negativeButtonText, (dialog, which) -> onNegative.onTextSet(input.getText().toString()));
        }

        if (onDismiss != null) builder.setOnDismissListener(onDismiss);

        dialogHolder[0] = builder.create();
        dialogHolder[0].setCanceledOnTouchOutside(false);
        dialogHolder[0].show();
    }

    public static void textInputWithSpinner(Activity activity, int titleText, String initialText,
                                            final String[] spinnerValues,
                                            int positiveButtonText,
                                            final TextAndSpinnerSetListener onPositive,
                                            int neutralButtonText,
                                            final TextAndSpinnerSetListener onNeutral,
                                            int negativeButtonText,
                                            final TextAndSpinnerSetListener onNegative,
                                            final DialogInterface.OnDismissListener onDismiss) {
        final EditText input = new EditText(activity);
        input.setSingleLine();
        if (initialText != null) {
            input.setText(initialText);
            Selection.setSelection(input.getText(), initialText.length());
        }

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        final Spinner spinner = new Spinner(activity);
        final ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            activity,
            android.R.layout.simple_spinner_item,
            spinnerValues);

        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setDialogLayoutPadding(activity, layout);
        layout.addView(input);
        layout.addView(spinner);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setTitle(titleText).setView(layout)
            .setPositiveButton(positiveButtonText, (d, whichButton) ->
                onPositive.onTextAndSpinnerSet(input.getText().toString(), spinner.getSelectedItemPosition()));

        if (onNeutral != null) {
            builder.setNeutralButton(neutralButtonText, (dialog, which) ->
                onNeutral.onTextAndSpinnerSet(input.getText().toString(), spinner.getSelectedItemPosition()));
        }

        if (onNegative == null) {
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setNegativeButton(negativeButtonText, (dialog, which) ->
                onNegative.onTextAndSpinnerSet(input.getText().toString(), spinner.getSelectedItemPosition()));
        }

        if (onDismiss != null) builder.setOnDismissListener(onDismiss);

        dialogHolder[0] = builder.create();
        dialogHolder[0].setCanceledOnTouchOutside(false);
        dialogHolder[0].show();
    }
}
