package com.group7.shop.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.stereotype.Component;

/**
 * 商品富文本描述的白名单过滤：允许常用排版、链接、图片与视频，
 * 禁止 script / iframe / style 与所有事件属性，防止 XSS。
 */
@Component
public class RichTextSanitizer {

    private static final Whitelist WHITELIST = Whitelist.relaxed()
            .addTags("video", "source")
            .addAttributes("video", "src", "controls", "width", "height", "poster")
            .addAttributes("source", "src", "type")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("video", "src", "http", "https")
            .addProtocols("source", "src", "http", "https");

    public String clean(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, WHITELIST);
    }
}