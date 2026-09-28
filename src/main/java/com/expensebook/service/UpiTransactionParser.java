package com.expensebook.service;

import com.expensebook.model.UpiTransaction;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent parser for Indian UPI transactional SMS and App Notifications.
 * Parses PhonePe, Google Pay, Paytm, CRED, BHIM, and major Indian banks (HDFC, SBI, ICICI, Axis, Kotak, PNB, etc.).
 */
public class UpiTransactionParser {

    private static final List<Pattern> AMOUNT_PATTERNS = List.of(
            Pattern.compile("(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:debited\\s+by|paid|sent)\\s*(?:INR|Rs\\.?|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:INR|Rs\\.?|₹)?\\s*(?:debited|deducted|spent)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> MERCHANT_PATTERNS = List.of(
            Pattern.compile("(?:paid\\s+to|sent\\s+to|transfer(?:red)?\\s+to|to|at|towards)\\s+([a-zA-Z0-9\\s&'\\.\\-_]{2,35}?)(?=\\s+(?:on|via|using|ref|upi|txn|vpa|avail|bal|from|acct|a/c|-|$|\\.|\\,))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("info:\\s*upi/[0-9]+/([a-zA-Z0-9\\s&'\\.\\-_]{2,35})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vpa\\s+([a-zA-Z0-9\\.\\-_]+)@", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:at)\\s+([a-zA-Z0-9\\s&'\\.\\-_]{2,30}?)(?=\\s+(?:on|via|using|ref|txn|-|$|\\.|\\,))", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> UPI_REF_PATTERNS = List.of(
            Pattern.compile("(?:upi\\s*(?:reference|ref|txn)?\\s*(?:no|id|#)?[\\s:]+|utr(?:\\s*no)?[\\s:]+|txn\\s*id[\\s:]+|ref\\s*no[\\s:]+)\\s*([a-zA-Z0-9]{6,25})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([0-9]{12})\\b")
    );

    private static final Pattern CREDIT_PATTERN = Pattern.compile("\\b(credited|received|refund(?:ed)?|cashback|deposited)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBIT_PATTERN = Pattern.compile("\\b(debited|paid|sent|transfer(?:red)?|spent|withdrawn|purchase|deducted)\\b", Pattern.CASE_INSENSITIVE);

    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("Food", List.of(
                "swiggy", "zomato", "starbucks", "mcdonald", "kfc", "burger", "pizza", "domino",
                "chai", "tea", "coffee", "cafe", "restaurant", "dhaba", "bakery", "biryani",
                "subway", "haldiram", "barbeque", "eats", "dine", "kitchen", "hotel", "canteen",
                "baker", "sweet", "chaat", "mithai", "tiffin"
        ));
        CATEGORY_KEYWORDS.put("Travel", List.of(
                "uber", "ola", "rapido", "irctc", "metro", "petrol", "fuel", "diesel", "hpcl",
                "bpcl", "iocl", "shell", "fastag", "toll", "parking", "flight", "indigo", "railway",
                "bus", "redbus", "travel", "cab", "taxi", "airways", "air india", "auto"
        ));
        CATEGORY_KEYWORDS.put("Groceries", List.of(
                "blinkit", "zepto", "instamart", "bigbasket", "dmart", "supermarket", "grocery",
                "groceries", "kirana", "vegetable", "fruits", "milk", "dairy", "fresh", "store",
                "provision", "mart", "bazaar", "more retail"
        ));
        CATEGORY_KEYWORDS.put("Shopping", List.of(
                "amazon", "flipkart", "myntra", "meesho", "ajio", "zara", "h&m", "nykaa",
                "shopping", "retail", "croma", "reliance digital", "tata cliq", "lifestyle",
                "westside", "decathlon", "mall", "electronics", "clothing", "apparel", "footwear"
        ));
        CATEGORY_KEYWORDS.put("Entertainment", List.of(
                "netflix", "spotify", "prime", "hotstar", "bookmyshow", "pvr", "inox", "cinema",
                "movie", "theatre", "gaming", "playstation", "steam", "youtube", "entertainment",
                "concert", "disney"
        ));
        CATEGORY_KEYWORDS.put("Health", List.of(
                "apollo", "pharmeasy", "1mg", "netmeds", "medplus", "hospital", "clinic",
                "pharmacy", "medical", "medicine", "doctor", "dentist", "lab", "diagnostic",
                "health", "chemist", "healthcare"
        ));
        CATEGORY_KEYWORDS.put("Bills", List.of(
                "jio", "airtel", "vi", "vodafone", "bescom", "electricity", "tata power", "adani",
                "gas", "indane", "hp gas", "water", "broadband", "wifi", "recharge", "bill",
                "dth", "tataplay", "utility", "utilities", "broadband", "act fibernet"
        ));
        CATEGORY_KEYWORDS.put("Education", List.of(
                "udemy", "coursera", "school", "college", "university", "tuition", "books",
                "stationary", "coaching", "library", "classes", "academy"
        ));
        CATEGORY_KEYWORDS.put("Rent", List.of(
                "nobroker", "nestaway", "rent", "landlord", "society maintenance", "maintenance"
        ));
        CATEGORY_KEYWORDS.put("Investment", List.of(
                "zerodha", "groww", "upstox", "angel", "sip", "mutual fund", "indmoney",
                "kuvera", "coin", "stock", "nse", "bse"
        ));
    }

    public static UpiTransaction parse(String rawText, String sourceHint) {
        UpiTransaction txn = new UpiTransaction();
        txn.setRawMessage(rawText);

        if (rawText == null || rawText.trim().isEmpty()) {
            txn.setParsedSuccessfully(false);
            txn.setStatusMessage("Empty message text.");
            return txn;
        }

        String text = rawText.trim();
        String detectedSource = detectSourceApp(text, sourceHint);
        txn.setSourceApp(detectedSource);

        boolean isCredit = CREDIT_PATTERN.matcher(text).find();
        boolean isDebit = DEBIT_PATTERN.matcher(text).find();

        if (isCredit && !isDebit) {
            txn.setDebit(false);
        } else {
            txn.setDebit(true);
        }

        Double amount = extractAmount(text);
        if (amount == null || amount <= 0) {
            txn.setParsedSuccessfully(false);
            txn.setStatusMessage("Could not extract a valid amount from the text.");
            return txn;
        }
        txn.setAmount(amount);

        String merchant = extractMerchant(text);
        if (merchant == null || merchant.isEmpty()) {
            merchant = "UPI Merchant";
        }
        txn.setMerchant(merchant);

        String upiRef = extractUpiRef(text);
        if (upiRef == null || upiRef.isEmpty()) {
            upiRef = "UPI" + Math.abs(Objects.hash(amount, merchant, LocalDate.now())) % 1000000000000L;
        }
        txn.setUpiRef(upiRef);

        String category = categorizeMerchant(merchant, text);
        txn.setCategoryName(category);

        txn.setTransactionDate(LocalDate.now());
        txn.setParsedSuccessfully(true);
        txn.setStatusMessage(String.format("Successfully detected ₹%.2f %s to %s via %s",
                amount, txn.isDebit() ? "paid" : "received", merchant, detectedSource));

        return txn;
    }

    private static Double extractAmount(String text) {
        for (Pattern pattern : AMOUNT_PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String amtStr = m.group(1).replace(",", "").trim();
                try {
                    return Double.parseDouble(amtStr);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static String extractMerchant(String text) {
        for (Pattern pattern : MERCHANT_PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String candidate = m.group(1).trim();
                candidate = candidate.replaceAll("(?i)^(vpa|upi/|to\\s+|at\\s+)", "").trim();
                candidate = candidate.replaceAll("(?i)\\s+(on|via|using|ref|upi|txn).*$", "").trim();
                candidate = candidate.replaceAll("[^a-zA-Z0-9\\s&'\\-_]", "").trim();
                if (candidate.length() >= 2 && !candidate.equalsIgnoreCase("upi") && !candidate.equalsIgnoreCase("bank")) {
                    return formatTitleCase(candidate);
                }
            }
        }
        return null;
    }

    private static String extractUpiRef(String text) {
        for (Pattern pattern : UPI_REF_PATTERNS) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private static String detectSourceApp(String text, String sourceHint) {
        if (sourceHint != null && !sourceHint.trim().isEmpty() && !sourceHint.equalsIgnoreCase("unknown")) {
            return sourceHint.trim();
        }

        String lower = text.toLowerCase();
        if (lower.contains("phonepe")) return "PhonePe";
        if (lower.contains("google pay") || lower.contains("gpay")) return "Google Pay";
        if (lower.contains("paytm")) return "Paytm";
        if (lower.contains("cred")) return "CRED";
        if (lower.contains("bhim")) return "BHIM";
        if (lower.contains("hdfc")) return "HDFC Bank";
        if (lower.contains("sbi") || lower.contains("state bank")) return "SBI";
        if (lower.contains("icici")) return "ICICI Bank";
        if (lower.contains("axis")) return "Axis Bank";
        if (lower.contains("kotak")) return "Kotak Bank";
        if (lower.contains("pnb") || lower.contains("punjab national")) return "PNB";
        if (lower.contains("bob") || lower.contains("bank of baroda")) return "Bank of Baroda";
        if (lower.contains("indusind")) return "IndusInd Bank";
        if (lower.contains("canara")) return "Canara Bank";
        return "UPI";
    }

    public static String categorizeMerchant(String merchant, String fullText) {
        String combined = (merchant + " " + fullText).toLowerCase();

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (combined.contains(kw)) {
                    return entry.getKey();
                }
            }
        }
        return "Other";
    }

    private static String formatTitleCase(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)))
                    .append(w.substring(1).toLowerCase())
                    .append(" ");
        }
        return sb.toString().trim();
    }
}
