/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.amazonrandomizer.util;

import com.amazon.advertising.api.sample.SignedRequestsHelper;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class holds methods to get random items from amazon.
 *
 * Got some help with signing requests from here:
 * https://aws.amazon.com/code/Product-Advertising-API/2478
 *
 * @author nick
 */
public class AmazonAPIUtils {

    private static final String AWS_ACCESS_KEY_ID = Constants.awsAccessKeyId;
    private static final String AWS_SECRET_KEY = Constants.awsSecretKey;
    private static final String AWS_ASSOCIATE_TAG = Constants.awsAssociateTag;
    private static final String AWS_ENDPOINT = Constants.awsEndpoint;
    private static String requestUrl; // the url that returns the first page of search results
    private static int totalPages;
    private static List<String> items = new ArrayList<String>(); // all the items to choose from
//    private static Map<String, String> stuff = new HashMap<>(); // use this instead? to hold item numbers and their detail page urls?
    private static String maxPrice = "500"; // TODO replace this with the price set by the user, also factor in the percentage paypal takes by subracting it
    private static String minPrice = "400"; // TODO replace this with a price that a dollar or so below the maxPrice
    private static String searchIndex; // TODO make this a funtion that returns a random search index
    private static String keywords; // TODO make this a function that returns random keywords
    private static List<String> searchIndexList = Arrays.asList(
            "UnboxVideo", "Appliances", "ArtsAndCrafts", "Automotive", "Baby", 
            "Beauty", "Music", "Wireless", "Fashion", "FashionBaby", 
            "FashionBoys", "FashionGirls", "FashionMen", "FashionWomen", 
            "Collectibles", "PCHardware", "Electronics", "GiftCards", "Grocery", 
            "HealthPersonalCare", "HomeGarden", "Industrial", "Luggage", 
            "Magazines", "MusicalInstruments", "OfficeProducts", 
            "LawnAndGarden", "PetSupplies", "Pantry", "SportingGoods", "Tools", 
            "Toys", "VideoGames"); // I left out a few that I think would be things that don't get shipped, like mp3 downloads and movies

    /**
     * the main guts of this class
     *
     * this method will first call the api with some random parameters to get
     * the first page of results, it also gets the first 10 results. according
     * to the documentation, pages hold 10 results, and you can only get the
     * first 10 pages of results, even if there are more. so we can get a
     * maximum of 100 random items to choose from. if none of those work we will
     * just have to do another search with new random parameters.
     *
     * the parameters that are random are the search index and keywords. it
     * would be nice to not have to use keywords, but the api seems to not work
     * without it, even though the docs say it is not required. the search index
     * is just the category. this works nice for us because we can use it to
     * make sure we don't buy an mp3 or something else that we can't really ship
     * to the user. we can also try to use it to prevent us from shipping things
     * that the user probably wouldn't appreciate, like porn...
     *
     * @return
     */
    public static String getRandomItem() { // TODO maybe pass the list of categories in through here so we can do another search if the item returned is no good
        String item = null;
        items.clear();

        // set random search parameters, searchIndex and keywords
        Random rand = new Random();
        searchIndex = searchIndexList.get(rand.nextInt(searchIndexList.size())); // set range with list size so things can be added or removed
        keywords = "words"; // TODO how to get random keywords?

        // get the first results and the number of pages of results
        getFirstPage();

        // if there is more than one page, get the rest of the results
        if (totalPages > 1) {
            getAllPages();
        }

        // test items list
        System.out.println("number of items: " + items.size());

        // choose one item at random
        if (items.size() > 0) {
            item = items.get(rand.nextInt(items.size()));
        }

        System.out.println("search index: " + searchIndex);
        System.out.println("keywords: " + keywords);

        // finally, return the item
        return item;
    }

    private static List<String> getItems(String requestUrl) {
        List<String> items = new ArrayList<String>();

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(requestUrl);
            if (doc != null) {
                NodeList asinNodes = doc.getElementsByTagName("ASIN");
                if (asinNodes != null && asinNodes.getLength() > 0) {
                    for (int i = 0; i < asinNodes.getLength(); i++) {
                        if (asinNodes.item(i).getNodeType() == Node.ELEMENT_NODE && asinNodes.item(i).getNodeName().contains("ASIN")) {
                            items.add(asinNodes.item(i).getTextContent());
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException | DOMException e) {
            throw new RuntimeException(e);
        }

//        for (String item : items) {
//            System.out.println("item: " + item);
//        }
        return items;
    }

    /**
     * Gets the number of pages in the search result.
     *
     * @param requestUrl
     * @return
     */
    private static int getTotalPages(String requestUrl) {
        int pages = 0;

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(requestUrl);
            if (doc != null) {
                Node totalPagesNode = doc.getElementsByTagName("TotalPages").item(0); // first one should be the one we want
                pages = Integer.parseInt(totalPagesNode.getTextContent());
            }
        } catch (ParserConfigurationException | SAXException | IOException | DOMException e) {
            throw new RuntimeException(e);
        }

        return pages;
    }

    /**
     * gets the first results, sets the totalPages variable
     */
    private static void getFirstPage() {
        try {
            SignedRequestsHelper helper;
            helper = SignedRequestsHelper.getInstance(AWS_ENDPOINT, AWS_ACCESS_KEY_ID, AWS_SECRET_KEY);

            // this is for the initial search
            Map<String, String> params = new HashMap<>();
            params.put("Service", "AWSECommerceService");
            params.put("AWSAccessKeyId", AWS_ACCESS_KEY_ID);
            params.put("AssociateTag", AWS_ASSOCIATE_TAG);
            params.put("Operation", "ItemSearch");
            params.put("MaximumPrice", maxPrice);
            params.put("MinimumPrice", minPrice);
            params.put("SearchIndex", searchIndex);
            params.put("Keywords", keywords);
            params.put("ResponseGroup", "ItemIds"); // TODO look into different response groups that might suit our needs better
            params.put("Timestamp", new SimpleDateFormat("YYYYMMDD").format(new Date()));

            // this is the url that returns items
            requestUrl = helper.sign(params);
//            System.out.println("request url: " + requestUrl);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Thread.sleep(500);
            Document doc = db.parse(requestUrl);
            if (doc != null) {

                // get total pages first
                Node totalPagesNode = doc.getElementsByTagName("TotalPages").item(0); // first one should be the only one
                totalPages = Integer.parseInt(totalPagesNode.getTextContent());
//                System.out.println("total pages: " + totalPages);

                // get items 
                NodeList asinNodes = doc.getElementsByTagName("ASIN");
                if (asinNodes != null && asinNodes.getLength() > 0) {
                    for (int i = 0; i < asinNodes.getLength(); i++) {
                        if (asinNodes.item(i).getNodeType() == Node.ELEMENT_NODE
                                && asinNodes.item(i).getNodeName().contains("ASIN")
                                && !items.contains(asinNodes.item(i).getTextContent())) {
                            items.add(asinNodes.item(i).getTextContent());

                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException | DOMException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeyException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void getAllPages() {
        try {
            SignedRequestsHelper helper;
            helper = SignedRequestsHelper.getInstance(AWS_ENDPOINT, AWS_ACCESS_KEY_ID, AWS_SECRET_KEY);

            // we already have page one, start at page two
            // the api will only return up to 10 pages
            for (int i = 2; i <= totalPages && i <= 10; i++) {
                // this is for searching the rest of the pages
                Map<String, String> params = new HashMap<>();
                params.put("Service", "AWSECommerceService");
                params.put("AWSAccessKeyId", AWS_ACCESS_KEY_ID);
                params.put("AssociateTag", AWS_ASSOCIATE_TAG);
                params.put("Operation", "ItemSearch");
                params.put("MaximumPrice", maxPrice);
                params.put("MinimumPrice", minPrice);
                params.put("SearchIndex", searchIndex);
                params.put("ItemPage", Integer.toString(i));
                params.put("Keywords", keywords);
                params.put("ResponseGroup", "ItemIds"); // TODO look into different response groups that might suit our needs better
                params.put("Timestamp", new SimpleDateFormat("YYYYMMDD").format(new Date()));

                // this is the url that returns items
                requestUrl = helper.sign(params);
//                System.out.println("request url: " + requestUrl);

                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Thread.sleep(1000);
                Document doc = db.parse(requestUrl);
                if (doc != null) {

                    // get items 
                    NodeList asinNodes = doc.getElementsByTagName("ASIN");
                    if (asinNodes != null && asinNodes.getLength() > 0) {
                        for (int j = 0; j < asinNodes.getLength(); j++) {
                            if (asinNodes.item(j).getNodeType() == Node.ELEMENT_NODE
                                    && asinNodes.item(j).getNodeName().contains("ASIN")
                                    && !items.contains(asinNodes.item(j).getTextContent())) {
                                items.add(asinNodes.item(j).getTextContent());
                            }
                        }
                    }
                }
            }

        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeyException | SAXException | ParserConfigurationException | InterruptedException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * this function returns a string of all the xml that is returned from the 
     * item lookup api. it basically holds a lot of words that describe the item,
     * which will be used to test against the blacklist to make sure the item
     * is ok to send.
     * @param item
     * @return 
     */
    public static String getItemDetails(String item) {
        String details = null;

        try {
            SignedRequestsHelper helper;
            helper = SignedRequestsHelper.getInstance(AWS_ENDPOINT, AWS_ACCESS_KEY_ID, AWS_SECRET_KEY);

            // make a call to the ItemLookup Amazon API
            Map<String, String> params = new HashMap<>();
            params.put("Service", "AWSECommerceService");
            params.put("AWSAccessKeyId", AWS_ACCESS_KEY_ID);
            params.put("AssociateTag", AWS_ASSOCIATE_TAG);
            params.put("Operation", "ItemLookup");
            params.put("ItemId", item);
            params.put("ResponseGroup", "Large"); // get as much info back so we can test for bad words
            params.put("Timestamp", new SimpleDateFormat("YYYYMMDD").format(new Date()));

            // this is the url that retruns all the items details
            requestUrl = helper.sign(params);
//            System.out.println("item details request url: " + requestUrl);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Thread.sleep(1000);
            Document doc = db.parse(requestUrl);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            details = writer.getBuffer().toString().replaceAll("\n|\r", "");

        } catch (IllegalArgumentException | UnsupportedEncodingException | NoSuchAlgorithmException | InvalidKeyException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TransformerException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(AmazonAPIUtils.class.getName()).log(Level.SEVERE, null, ex);
        }

        return details;
    }
}
