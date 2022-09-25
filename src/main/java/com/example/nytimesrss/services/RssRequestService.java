package com.example.nytimesrss.services;

import java.io.*;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.example.nytimesrss.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.example.nytimesrss.model.RssFeed;

@Service
public class RssRequestService {

    @Value("${rssnytimes.path}")
    private String rssNytimesPath;

    @Value("${rssnytimes.dateformat}")
    private String dateFormat;

    private final String ITEM_ELEMENT_NAME = "item";
    private final String TITLE_ELEMENT_NAME = "title";
    private final String DESCRIPTION_ELEMENT_NAME = "description";
    private final String MEDIA_ELEMENT_NAME = "media:content";
    private final String HEIGHT_ATTRIBUTE_NAME = "height";
    private final String WIDTH_ATTRIBUTE_NAME = "width";
    private final String CREATOR_ELEMENT_NAME = "dc:creator";
    private final String CHANNEL_ELEMENT_NAME = "channel";
    private final String PUB_DATE_ELEMENT_NAME = "pubDate";
    private final String IMAGE_ELEMENT_NAME = "image";
    private final String LINK_ELEMENT_NAME = "link";
    private final String URL_ELEMENT_NAME = "url";

    private final RestTemplate restTemplate;

    private RssFeed rssFeed;

    public RssRequestService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    public RssFeed getRssFeed() {
        return rssFeed;
    }

    /**
     * Update content method. Executes every 15 minutes.
     */
    @Scheduled(fixedRate = 900000)
    public void updateContent() {
        rssFeed = new RssFeed();
        rssFeed.setItems(new ArrayList<>());
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            Optional<String> response = getRequest(rssNytimesPath);

            if (response.isPresent()) {
                Document document = builder.parse(new InputSource(new StringReader(response.get())));

                document.getDocumentElement().normalize();
                Element root = document.getDocumentElement();

                NodeList nodeList = root.getChildNodes();
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node channelElement = nodeList.item(i);
                    if (channelElement.getNodeType() == Node.ELEMENT_NODE && channelElement.getNodeName().equals(CHANNEL_ELEMENT_NAME)) {
                        NodeList channelChildNodes = channelElement.getChildNodes();
                        for (int j = 0; j < channelChildNodes.getLength(); j++) {
                            Node node = channelChildNodes.item(j);

                            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(IMAGE_ELEMENT_NAME)) {
                                NodeList childNodes = node.getChildNodes();
                                for (int k = 0; k < childNodes.getLength(); k++) {
                                    Node childNode = childNodes.item(k);
                                    if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getNodeName().equals(LINK_ELEMENT_NAME)) {
                                        rssFeed.setLink(childNode.getFirstChild().getNodeValue());
                                    }
                                    if (childNode.getNodeType() == Node.ELEMENT_NODE && childNode.getNodeName().equals(URL_ELEMENT_NAME)) {
                                        getImage(childNode.getFirstChild().getNodeValue()).ifPresent(rssFeed::setImage);
                                    }
                                }
                            }
                            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(ITEM_ELEMENT_NAME)) {
                                Item item = new Item();
                                NodeList childNodes = node.getChildNodes();
                                for (int k = 0; k < childNodes.getLength(); k++) {
                                    Node childNode = childNodes.item(k);
                                    if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                                        switch (childNode.getNodeName()) {
                                            case PUB_DATE_ELEMENT_NAME:
                                                getNodeValue(childNode).ifPresent(stringValue -> parseStringToDate(stringValue)
                                                        .ifPresent(item::setPubDate));
                                                break;
                                            case TITLE_ELEMENT_NAME:
                                                getNodeValue(childNode).ifPresent(item::setTitle);
                                                break;
                                            case LINK_ELEMENT_NAME:
                                                getNodeValue(childNode).ifPresent(item::setLink);
                                                break;
                                            case DESCRIPTION_ELEMENT_NAME:
                                                getNodeValue(childNode).ifPresent(item::setDescription);
                                                break;
                                            case MEDIA_ELEMENT_NAME:
                                                Element element = (Element) childNode;
                                                if (element.hasAttribute(URL_ELEMENT_NAME)) {
                                                    getImage(element.getAttribute(URL_ELEMENT_NAME)).ifPresent(item::setImage);
                                                }
                                                break;
                                            case CREATOR_ELEMENT_NAME:
                                                getNodeValue(childNode).ifPresent(item::setCreatorsNames);
                                                break;
                                        }
                                    }
                                }
                                rssFeed.getItems().add(item);
                            }
                            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(PUB_DATE_ELEMENT_NAME)) {
                                if (node.hasChildNodes()) {
                                    parseStringToDate(node.getFirstChild().getNodeValue())
                                            .ifPresent(rssFeed::setPubDate);
                                }
                            }
                        }
                    }
                }
            }
            System.out.println(rssFeed);

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            ex.printStackTrace();
        }
    }

    private Optional<byte[]> getImage(String urlString) throws IOException {
        URL url = new URL(urlString);
        try (InputStream in = new BufferedInputStream(url.openStream());
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int n = 0;
            while ((n = in.read()) != -1) {
                out.write(n);
            }
            return Optional.of(out.toByteArray());
        }
    }

    private Optional<String> getNodeValue(Node node) {
        if (node.hasChildNodes()) {
            return Optional.of(node.getFirstChild().getNodeValue());
        }
        return Optional.empty();
    }

    private Optional<String> getRequest(String path) {
        String response = this.restTemplate.getForObject(path, String.class);
        if (response == null) {
            return Optional.empty();
        }
        return Optional.of(response);
    }

    private Optional<Date> parseStringToDate(String str) {
        try {
            return Optional.of(new SimpleDateFormat(dateFormat).parse(str));
        } catch (ParseException ex) {
            return Optional.empty();
        }
    }
}
