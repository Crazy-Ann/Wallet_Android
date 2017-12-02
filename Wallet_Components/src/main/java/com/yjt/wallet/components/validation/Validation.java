package com.yjt.wallet.components.validation;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import com.yjt.wallet.components.utils.ViewUtil;

public class Validation {

    private String format;
    private EditText editText;
    private boolean isRelateButton;
    private View button;
    private ValidationExecutor validationExecutor;

    public Validation(String format, EditText editText, boolean isRelateButton, View button, ValidationExecutor validationExecutor) {
        this.format = format;
        this.editText = editText;
        this.isRelateButton = isRelateButton;
        this.button = button;
        this.validationExecutor = validationExecutor;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public EditText getEditText() {
        return editText;
    }

    public Validation setEditText(EditText editText) {
        this.editText = editText;
        return this;
    }

    public boolean isRelateButton() {
        return isRelateButton;
    }

    public void setRelateButton(boolean relateButton) {
        this.isRelateButton = relateButton;
    }

    public View getButton() {
        return button;
    }

    public void setButton(View button) {
        this.button = button;
    }

    public ValidationExecutor getValidationExecutor() {
        return validationExecutor;
    }

    public Validation setValidationExecutor(ValidationExecutor validationExecutor) {
        this.validationExecutor = validationExecutor;
        return this;
    }

    public boolean isTextEmpty() {
        if (editText == null || TextUtils.isEmpty(editText.getText())) {
            ViewUtil.getInstance().setViewGone(button);
            return true;
        }
        ViewUtil.getInstance().setViewVisible(button);
        return false;
    }

}
