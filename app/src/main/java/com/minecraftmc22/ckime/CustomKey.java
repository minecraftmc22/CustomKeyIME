package com.minecraftmc22.ckime;

/**
 * 一个自定义按键：text 为要输入的内容，autoEnter 表示输入后是否自动按回车（发送）。
 */
public class CustomKey {
    public final String text;
    public final boolean autoEnter;

    public CustomKey(String text, boolean autoEnter) {
        this.text = text;
        this.autoEnter = autoEnter;
    }
}
