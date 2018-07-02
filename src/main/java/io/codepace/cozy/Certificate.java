package io.codepace.cozy;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides all functionality related to certificate verification and usage.
 * A certificate contains:
 * -Redeem address
 * -Arbitrary data section (used for pool mining, mostly)
 * -Max nonce
 * -Authority name
 * -Signature
 * <p>
 * Certificate string layout:
 * {redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{signatureData},{signatureIndex}
 * Message is {redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash}
 * Message includes the formatting separators ':' and '{}'
 */
public class Certificate {
    public String redeemAddress;
    public String arbitraryData;
    public int maxNonce;
    public String authorityName;
    public int blockNum;
    public String prevBlockHash;
    public long signatureIndex;
    public String signatureData;
    public static final Map<String, String> authorities;

    // Authorities are not (in this version) going to be altered while running.
    static {

        authorities = new HashMap<String, String>(2);
        authorities.put("Codepace", "A1H6CHCCRZZKW67NRSUHCQGWI4GWVYOCXGKYF6"); //Testnet authority 1
    }

    /**
     * Determines whether the certificate is PoW (complete certificate). If the certificate
     * is filled with zeros (other than redeem address, maxNonce, blockNum, and prevBlockHash), then it is PoS instead.
     *
     * @return boolean Whether the Certificate is PoW
     */
    public boolean isPoWCertificate() {
        if (!isAllZeroes(arbitraryData)) {
            return true;
        }
        if (!isAllZeroes(authorityName)) {
            return true;
        }
        if (signatureIndex != 0) {
            return true;
        }
        if (!isAllZeroes(signatureData)) {
            return true;
        }
        return false;
    }

    private boolean isAllZeroes(String toTest) {
        for (int i = 0; i < toTest.length(); i++) {
            // Commas allowed as separators...
            if (toTest.charAt(0) != '0' && toTest.charAt(0) != ',') {
                return false;
            }
        }
        return true;
    }

    /**
     * Constructor for Certificate object. A constructed certificate object is not necessarily a valid certificate for the network--be sure to call validateCertificate()!
     * As a program structure/design choice, validateCertificate() is not called by the constructor, as the constructor has no reasonable method for notifying the object
     * creator of the formatting or signature verification problems.
     * Currently, authority names are hard-coded with special (less than 18 layers) addresses in a {@link HashMap}.
     * In the future, these will be drawn from some external source, and the signature authorities will have robust control over this.
     * 0.2.01 is to prove that stuff works and stress test other components of the network--protocol-level support for signature authorities to change their address
     * will probably be demonstrated in 0.2.02. Maybe a3.
     *
     * @param redeemAddress  The address the coinbase will be sent to if this certificate were to mine a block
     * @param arbitraryData  Arbitrary data added to the certificate, could be an address for P2Pool, or a username/worker name for a traditional pool.
     * @param maxNonce       The maximum nonce in the certificate.
     * @param authorityName  The human-readable name of the signature authority.
     * @param blockNum       The block number this certificate is valid for mining
     * @param prevBlockHash  The hash of the previous block a block made using this certificate would stack on top of
     * @param signatureIndex The signature index used when signing this certificate, used for certificate verification.
     * @param signatureData  The actual ugly signature that signs the entirety of the certificate message
     */
    public Certificate(String redeemAddress, String arbitraryData, int maxNonce, String authorityName, int blockNum, String prevBlockHash, long signatureIndex, String signatureData) {
        this.redeemAddress = redeemAddress;
        this.arbitraryData = arbitraryData;
        this.maxNonce = maxNonce;
        this.authorityName = authorityName;
        this.blockNum = blockNum;
        this.prevBlockHash = prevBlockHash;
        this.signatureIndex = signatureIndex;
        this.signatureData = signatureData;
    }

    /**
     * Alternate constructor to make a Certificate from the raw certificate string. See above constructor comment for more detailed information.
     *
     * @param rawCertificate String formatted as raw certificate data
     */
    public Certificate(String rawCertificate) {
        String[] certificateParts = rawCertificate.split(",");
        String[] firstPartPart = certificateParts[0].replace("{", "").replace("}", "").split(":"); //At least I'm consistent
        try {
            this.redeemAddress = firstPartPart[0];
            this.arbitraryData = firstPartPart[1];
            this.maxNonce = Integer.parseInt(firstPartPart[2]);
            this.authorityName = firstPartPart[3];
            this.blockNum = Integer.parseInt(firstPartPart[4]);
            this.prevBlockHash = firstPartPart[5];
            this.signatureData = certificateParts[1].replace("{", "") + "," + certificateParts[2].replace("}", "");
            this.signatureIndex = Long.parseLong(certificateParts[3].replace("{", "").replace("}", ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Used to check a variety of conditions to ensure that a certificate is valid.
     * Valid certificate requirements:
     * -AuthorityName resolves properly to an address (stored in the {@link HashMap} called authorities)
     * -Signature signs the certificate message properly and is valid for the given signature index
     * -redeemAddress is formatted correctly, the hash (last four characters) matches the TreeRoot
     * -arbitraryData is 40 or fewer characters
     *
     * @return boolean Whether the certificate is valid
     */
    public boolean validateCertificate() {
        String authorityAddress = "";
        if (authorities.containsKey(authorityName)) {
            authorityAddress = authorities.get(authorityName);
        } else {
            return false; //Not a valid authority
        }
        MerkleAddressUtility MerkleAddressUtility = new MerkleAddressUtility();
        if (!MerkleAddressUtility.verifyMerkleSignature(getCertificateMessage(), signatureData, authorityAddress, signatureIndex)) {
            return false; //Signature is not valid
        }
        if (!MerkleAddressUtility.isAddressFormattedCorrectly(redeemAddress)) {
            return false; //Destination redemption address is invalid
        }
        if (arbitraryData.length() > 40) {
            return false; //Arbitrary data section can only be 40 characters or less
        }
        if (maxNonce <= 0) {
            return false; //maxNonce must be a positive integer
        }
        return true;
    }

    /**
     * This method finds the nonce (between 0 and maxNonce) that provides the lowest difficulty score.
     * Used only for mining, lower difficulty scores are better. Peers do not need to run this when confirming certificates.
     * When a peer checks a certificate for mining rights, it simply checks the nonce provided by a peer.
     * Theoretically, a certificate could have several nonces which solve a block, but it wouldn't matter.
     * <p>
     * Performance is more than adequate for intended purpose. Anyone suggesting I multithread this will be ignored.
     * As this is only called by the miner once, and then never used again for a given certificate, it just has to work well.
     * On one core of an i7-3770K clocked at 3.9GHz, this can perform approximately 647,249 nonce checks per second. YMMV.
     * Using FAH as an example, say a WU was worth 30,000 'points' and for each point, FAH gave you one nonce in the certificate.
     * That seems reasonable.
     * So this method would take 0.04635 seconds on a high-end desktop. You ran a GPU for 6 hours to finish that WU.
     * You can afford to wait 0.04635 seconds to check to see if you earned any coins. :)
     * Originally, the certificate used for mining was going to have the signature used in the hash.
     * However, this provides no additional security (transitive property; if the signature matches the message and the message
     * when a nonce is appended hashes to below the difficulty, it is a legitimate address.) and was removed for performance
     * considerations. Hashing that much data unnecessarily was decided to be a terrible idea.
     * <p>
     * Here's how difficulty works: the 'difficulty' grows higher as more people do more computational science per second.
     * A nonce's hash score has to be under a 'target' in order to mine a block.
     * Therefore, as the difficulty grows, the target shrinks. The target is Long.MAX_VALUE / (difficulty/2). The /2 accounts
     * for half of the numbers being negative.
     * <p>
     * At a difficulty of 10, 1/10 nonces will be a solution.
     * At a difficulty of 100, 1/100 nonces will be a solution.
     * At a difficulty of 1000, 1/1000 nonces will be a solution. Etc.
     *
     * @return String Best nonce in certificate range with the corresponding difficulty. Format: "bestNonce:difficulty"
     */
    public String getMinCertificateScoreWithNonce() {
        System.out.println("Checking up to " + maxNonce);
        long score = Long.MAX_VALUE;
        int bestNonce = -1;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String certificateMessage = getCertificateMessage();
            for (int i = 0; i < maxNonce; i++) {
                String certificateWithNonce = certificateMessage + ",{" + i + "}"; //Nonce is added in {}'s to fullCertificate for mining.
                //System.out.println(certificateWithNonce);
                byte[] hash = md.digest(certificateWithNonce.getBytes("UTF-8"));
                long tempScore = 0L;
                for (int j = 0; j < 8; j++) {
                    tempScore = (hash[j] & 0xff) + (tempScore << 8); //This takes the first 8 bytes of the hash, and turns it into a long. Works out nicely, as a Long is 64 bits.
                }
                /*
                 * Quick lesson on binary.
                 * We get to invent the rules here, because difficulty targets is really an arbitrary idea. It could be implemented in a number of ways that are opposite of each other,
                 * and still work perfectly fine. That being said, my method seems simple. I take the first 8 bytes of the hash, and turn that into a number.
                 * You'll notice that the number is bigger for two same-length pieces of binary when the first digit is a zero.
                 * This means the most significant bit is the left-most-bit.
                 * The above are examples of UNSIGNED conversion.
                 * Cause negative numbers are occasionally useful, Java's Long is signed. That means the first bit dictates whether the number is positive or negative.
                 * In what might appear counter-intuitive at first, if the first digit is a 1, the long is negative. If it is a 0, the long is positive.
                 * As a result, the largest possible unsigned values will start with a 1 as that signed bit position (left-most-bit) normally represents 2^63 on its own.
                 * Since a hash can be simplified as a mental exercise to spit out 'random' or unpredictable data which has no propensity towards any patterns, this first
                 * bit has a 50 percent chance every hash to be a 1. If it's a 1, it's a huge number anyway (2^63 plus change).
                 * As a result, there's no reason to even care about any negative longs, as they actually represent the biggest numbers (if longs were unsigned).
                 *
                 * 111111111111111111111111111111111111111111111111111111111111 is actually a negative number when converted, because a long is signed.
                 * If tempDiff is negative, it means its most significant bit is a 1, so if it wasn't signed, it would be huge.
                 * For this reason, it's incredibly safe to discard all negative numbers. If the Cozycoin network difficulty is so easy that a Long represented in binary
                 * starting with a 1 can solve it, I'll start folding on a raspberry pie and fix it. There would have to be, on average, only one certificate per three
                 * minutes, with only ONE allowed nonce (0) for the difficulty to drop this low. Aka Cozycoin would have to be abandoned. By everyone. At the same time.
                 * In that event, allowing the block time to slip to 6 minutes (the result of discarding a potential solution to the minimum difficulty 50 percent of the
                 * time) probably isn't the biggest concern.
                 */
                if (tempScore < score && tempScore > 0) //Longs are signed. Half of them will be negative, on average. Just throw out the negative ones. In unsigned-land, they're super huge and not suitable for mining anyhow. Read above. :)
                {
                    score = tempScore;
                    bestNonce = i;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Best nonce: " + bestNonce + " with score: " + score);
        return (bestNonce + ":" + score);
    }

    /**
     * This method will return the difficulty-related score at a given nonce. Generally, this method will be called when peers verify a certificate is under the difficulty
     * required to mine a block. The lower the score, the better. The score represents the long-representation of the first 64 bits of the hash of the certificate
     * with the provided nonce appended.
     *
     * @param nonce Integer to check the score of
     * @return long The score (lower is better) of the certificate at the given nonce
     */
    public long getScoreAtNonce(int nonce) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String fullCertificate = getFullCertificate();
            String certificateWithNonce = fullCertificate + ",{" + nonce + "}"; //Nonce is added in {}'s to fullCertificate for mining.
            byte[] hash = md.digest(certificateWithNonce.getBytes("UTF-8"));
            long score = 0L;
            for (int j = 0; j < 8; j++) {
                score = (hash[j] & 0xff) + (score << 8); //This takes the first 8 bytes of the hash, and turns it into a long. Works out nicely, as a Long is 64 bits.
            }
            if (score < 0) {
                /*Negative numbers mean the most significant bit (left-most-bit) is a 1. That's big, not small. So it shouldn't be negative.
                As explained above in excruciating detail (see the comment in getMinCertificateScoreWithNonce() for more info) difficulties with a starting 1 are worthless. */
                score = Long.MAX_VALUE;
            }
            return score;
        } catch (Exception e) {
            System.out.println("[CRITICAL ERROR] Unable to run getScoreAtNonce(" + nonce + ") on a certificate!");
            e.printStackTrace();
        }
        return Long.MAX_VALUE; //In case of a significant problem, don't tell anyone it's fine to use this certificate for mining. That could create some attack vector.
    }

    /**
     * Returns the full certificate for sending or storage.
     * Format: {redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash},{certificateSignatureData},{certificateSigantureIndex}
     *
     * @return String Full certificate
     */
    public String getFullCertificate() {
        return getCertificateMessage() + "," + "{" + signatureData + "},{" + signatureIndex + "}";
    }

    /**
     * Returns the message portion of the certificate.
     * Message format:
     * {redeemAddress:arbitraryData:maxNonce:authorityName:blockNum:prevBlockHash}
     *
     * @return String Message portion of certificate, containing redeem address, arbitrary data, max certificate nonce, authority name, block number,and previous block hash.
     */
    public String getCertificateMessage() {
        return "{" + redeemAddress + ":" + arbitraryData + ":" + maxNonce + ":" + authorityName + ":" + blockNum + ":" + prevBlockHash + "}";
    }
}