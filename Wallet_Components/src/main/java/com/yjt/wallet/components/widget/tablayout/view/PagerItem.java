package com.yjt.wallet.components.widget.tablayout.view;

public abstract class PagerItem {

    public static final float DEFAULT_WIDTH = 1.f;

    private final CharSequence title;
    private final float width;

    public PagerItem(CharSequence title, float width) {
        this.title = title;
        this.width = width;
    }

    public CharSequence getTitle() {
        return title;
    }

    public float getWidth() {
        return width;
    }

}
