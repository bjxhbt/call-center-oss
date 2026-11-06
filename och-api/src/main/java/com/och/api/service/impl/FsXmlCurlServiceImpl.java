package com.och.api.service.impl;

import com.och.api.factory.FsXmlCurlEventStrategyFactory;
import com.och.api.service.IFsXmlCurlService;
import com.och.security.utils.XmlCurlUtil;
import com.och.common.domain.FsXmlCurl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

;

/**
 * @author danmo
 * @date 2023年09月14日 14:07
 */
@Slf4j
@Service
public class FsXmlCurlServiceImpl implements IFsXmlCurlService {

    @Autowired
    private FsXmlCurlEventStrategyFactory fsXmlCurlEventStrategyFactory;

    @Override
    public String xmlHandle(HttpServletRequest request) {
        FsXmlCurl xmlCurl = XmlCurlUtil.decodeThenParse(request);
        String section = xmlCurl.getSection();
        log.debug("xmlCurl request section={}, action={}, hasUser={}, metadataKeyCount={}",
                section,
                xmlCurl.getMetadata() == null ? null : xmlCurl.getMetadata().get("action"),
                xmlCurl.getMetadata() != null && StringUtils.isNotBlank(xmlCurl.getMetadata().get("user")),
                xmlCurl.getMetadata() == null ? 0 : xmlCurl.getMetadata().size());
        if (StringUtils.isBlank(section)) {
            return "";
        }
        String keyValue = xmlCurl.getKeyValue();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<document type=\"freeswitch/xml\">");
        stringBuilder.append("<section name=\"").append(section).append("\">");
        try {
            String type = section.toUpperCase(Locale.ROOT);
            if(Objects.equals(section,"configuration")){
                type = (section + ":" + keyValue).toUpperCase(Locale.ROOT);
            }
            stringBuilder.append(fsXmlCurlEventStrategyFactory.getResource(type, xmlCurl));
        } catch (Throwable e) {
            // Strategy exceptions may originate from dynamically stored XML/JSON.
            // Do not include a message/stack trace because either can reproduce secrets.
            log.warn("Failed to build FreeSWITCH XML-curl response for section={}, errorType={}",
                    section, e.getClass().getSimpleName());
        }
        stringBuilder.append("</section>\n" +
                "</document>");
        log.debug("xmlCurl response section={}, length={}", section, stringBuilder.length());
        return stringBuilder.toString();
    }
}
