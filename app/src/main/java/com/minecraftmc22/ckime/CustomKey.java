package com.minecraftmc22.ckime;

/**
 * 一个自定义按键。
 *  - text: 点击后输入的内容
 *  - autoEnter: 输入后是否自动按回车（在聊天框中即自动发送）
 *  - groupName: 所属分组名
 */
public class CustomKey {
    public final String text;
    public final boolean autoEnter;
    public final String groupName;

    public CustomKey(String text, boolean autoEnter, String groupName) {
        this.text = text;
        this.autoEnter = autoEnter;
        this.groupName = groupName;
    }
}