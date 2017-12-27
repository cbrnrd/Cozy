package io.codepace.coffeecoin;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static io.codepace.coffeecoin.Util.*;

/**
 * This class provides all functionality related to cert usage and verification
 *
 * PROTOCOL RULE: A certificate contains:
 * - Redeemer address
 * - Arbitrary data section (for pool mining, etc.)
 * - Max nonce value
 * - Authority name
 * - Signature
 *
 * PROTOCOL RULE: The layout of a certificate looks like so:
 *  {redeemAddr:arbitraryData:maxNonce:authority:blockIndex:prevBlockHash},{signatureData},{signatureIndex}
 */
public class Certificate {
    public String redeemAddress;
    public String arbitraryData;
    public long maxNonce;
    public String authority;
    public int blockIndex;
    public String prevBlockHash;
    public long signatureIndex;
    public String signatureData;
    public static final Map<String, String> authorities;

    static {

        authorities = new HashMap<String, String>(2);
        authorities.put("Codepace", "A1H6CHCCRZZKW67NRSUHCQGWI4GWVYOCXGKYF6");  // Change?
    }


    public Certificate(String redeemAddress, String arbitraryData, long maxNonce, String authority, int blockIndex, String prevBlockHash, long signatureIndex, String signatureData){
        this.redeemAddress = redeemAddress;
        this.arbitraryData = arbitraryData;
        this.maxNonce = maxNonce;
        this.authority = authority;
        this.blockIndex = blockIndex;
        this.prevBlockHash = prevBlockHash;
        this.signatureIndex = signatureIndex;
        this.signatureData = signatureData;
    }

    public Certificate(String rawCert){
        String[] certificateParts = rawCert.split(",");
        String[] firstPartPart = certificateParts[0].replace("{", "").replace("}", "").split(":");
        try{
            this.redeemAddress = firstPartPart[0];
            this.arbitraryData = firstPartPart[1];
            this.maxNonce = Long.parseLong(firstPartPart[2]);
            this.authority = firstPartPart[3];
            this.blockIndex = Integer.parseInt(firstPartPart[4]);
            this.prevBlockHash = firstPartPart[5];
            this.signatureData = certificateParts[1].replace("{", "") + "," + certificateParts[2].replace("}", "");
            this.signatureIndex = Long.parseLong(certificateParts[3].replace("{", "").replace("}", ""));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * TODO, for now :nodoc:
     * @return
     */
    public boolean validCertificate() {
        String authorityAddr = "";
        if (authorities.containsKey(authority)){
            authorityAddr = authorities.get(authority);
        } else {
            return false;
        }

        MerkleAddressUtility merkleAddressUtility = new MerkleAddressUtility();
        if(!merkleAddressUtility.verifyMerkleSignature(getCertificateMessage(), signatureData, authorityAddr, signatureIndex)){
            return false; // Sig is invalid
        }
        if(!merkleAddressUtility.isAddressFormattedCorrectly(redeemAddress)){
            return false; // Dest addr is invalid
        }
        if(arbitraryData.length() > 64){
            return false; // arbitraryData must be less than 65 chars
        }
        if (maxNonce <= 0){
            return false; // nonce must be positive
        }
        return true; // all is well
    }

    public boolean isPoWCertificate() {
        if (!isAllZeroes(arbitraryData))
            return true;
        if (!isAllZeroes(authority))
            return true;
        if (signatureIndex != 0)
            return true;
        if (!isAllZeroes(signatureData))
            return true;
        return false;
    }

    public String getMinCertificateScoreWithNonce(){
        System.out.println("Checking nonce's upto " + maxNonce + "...");
        long score = Long.MAX_VALUE;
        int bestNonce = -1;
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String certMessage = getCertificateMessage();
            for (int i = 0; i < maxNonce; i++) {
                String certWithNonce = certMessage + ",{" + i + '}';
                byte[] hash = md.digest(certWithNonce.getBytes("UTF-8"));
                long tempScore = 0L;
                for (int j = 0; j < 8; j++) {
                    tempScore = (hash[j] & 0xFF) + (tempScore << 8); //takes first 8 bytes of the hash and converts it to a long
                }
                if(tempScore < score && tempScore > 0){ // Check for positivity
                    score = tempScore;
                    bestNonce = i;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        logInfoAndPrint("Best nonce:" + bestNonce + " with score: " + score);
        return bestNonce + ":" + score;
    }

    public long getScoreAtNonce(int nonce){
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String fullCert = getFullCertificate();
            String certWithNonce = fullCert + ",{" + nonce + '}';
            byte[] hash = md.digest(certWithNonce.getBytes("UTF-8"));
            long score = 0L;
            for (int i = 0; i < 8; i++) {
                score = (hash[i] & 0xFF) + (score << 8);
            }
            if (score < 0){
                score = Long.MAX_VALUE;
            }
            return score;
        } catch (Exception e){
            getLogger().severe("Unable to get score at nonce " + nonce + " on a certificate!");
            System.out.println(new Timestamp(System.currentTimeMillis()) + " [DAEMON] - " + ANSI_RED + "Unable to get score at nonce " + nonce + " on a certificate!" + ANSI_RESET);
        }
        return Long.MAX_VALUE;
    }

    public String getFullCertificate(){
        return getCertificateMessage() + ",{" + signatureData + "},{" + signatureIndex + "}";
    }

    public String getCertificateMessage(){
        return "{" + redeemAddress + ":" + arbitraryData + ":" + maxNonce + ":" + authority + ":" + blockIndex +  ":" + prevBlockHash + "}";
    }
}


