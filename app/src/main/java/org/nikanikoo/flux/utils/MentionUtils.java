package org.nikanikoo.flux.utils;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import org.nikanikoo.flux.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionUtils {
    private static final Pattern MENTION_PATTERN = Pattern.compile("\\[(id|club|public|group)(\\d+)\\|([^\\]]+)\\]");

    public interface OnMentionClickListener {
        void onMentionClick(int id, String name, boolean isGroup);
    }

    /**
     * Делает упоминания в тексте кликабельными ссылками
     * @param text Текст в котором есть ссылка типа [id123|Кто-то]
     * @param listener Коллбэк для нажатия на ссылку
     * @return CharSequence с кликабельными ссылками
     */
    public static CharSequence formatMentions(String text, OnMentionClickListener listener) {
        return formatMentions(text, listener, false);
    }

    /**
     * Делает упоминания в тексте кликабельными ссылками
     * @param text Текст в котором есть ссылка типа [id123|Кто-то]
     * @param listener Коллбэк для нажатия на ссылку
     * @param underlineText Подчёркивать ли текст в ссылке
     * @return CharSequence с кликабельными ссылками
     */
    public static CharSequence formatMentions(String text, OnMentionClickListener listener, boolean underlineText) {
        if (text == null || text.isEmpty()) return "";
        
        SpannableStringBuilder builder = new SpannableStringBuilder();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        int lastEnd = 0;
        
        while (matcher.find()) {
            //добавляем текст до упоминания
            builder.append(text, lastEnd, matcher.start());
            
            String type = matcher.group(1);
            String idStr = matcher.group(2);
            String name = matcher.group(3);
            
            if (idStr != null && name != null) {
                try {
                    final int id = Integer.parseInt(idStr);
                    final boolean isGroup = !"id".equals(type);
                    
                    int start = builder.length();
                    builder.append(name);
                    int end = builder.length();
                    
                    builder.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            if (listener != null) {
                                listener.onMentionClick(id, name, isGroup);
                            }
                        }
                        
                        @Override
                        public void updateDrawState(@NonNull android.text.TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setUnderlineText(underlineText);
                            //тут можно ставить всякие свойства ссылки
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (NumberFormatException e) {
                    //если id - не номер
                    builder.append(matcher.group());
                }
            } else {
                builder.append(matcher.group());
            }
            
            lastEnd = matcher.end();
        }
        
        //добавляем оставшийся текст
        builder.append(text, lastEnd, text.length());
        return builder;
    }
}
