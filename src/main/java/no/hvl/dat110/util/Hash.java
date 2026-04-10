package no.hvl.dat110.util;

/**
 * exercise/demo purpose in dat110
 * @author tdoy
 *
 */

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash { 
	
	
	public static BigInteger hashOf(String entity) {	
	
		BigInteger hashint = null;
		
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			
			byte[] digest = md.digest(entity.getBytes("UTF-8"));
			
			// convert to positive BigInteger
			hashint = new BigInteger(1, digest);
			
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		return hashint;
	}
	
	public static BigInteger addressSize() {
		
		int bits = bitSize();
		
		// 2^bits
		return BigInteger.valueOf(2).pow(bits);
	}
	
	public static int bitSize() {
		
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			return md.getDigestLength() * 8; // bytes → bits
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		return 0;
	}
	
	
	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for(byte b : digest) {
			strbuilder.append(String.format("%02x", b&0xff));
		}
		return strbuilder.toString();
	}

}
