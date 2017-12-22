package io.codepace.coffeecoin;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static io.codepace.coffeecoin.Util.isAllZeroes;

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
        authorities.put("Codepace", "A1H6CHCCRZZKW67NRSUHCQGWI4GWVYOCXGKYF6");
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
        System.out.println("Checking nonce's upto " + maxNonce);
        long score = Long.MAX_VALUE;
        int bestNonce = -1;
        try{
            MessageDigest md = MessageDigest.getInstance("SHA-256");
        }
    }
}


