package com.ociweb.pronghorn.pipe.util;

import java.io.IOException;

/**
 * 
 * Garbage free single pass utilities for building up text.
 * The API follows a fluent pattern where every method returns the same Appendable which was passed in.
 * 
 * @author Nathan Tippy
 *
 */
public class Appendables {
    
    private final static char[] hBase = new char[] {'0','1','2','3','4','5','6','7','8','9',
      'A','B','C','D','E','F'};
    
    public static <A extends Appendable> A appendArray(A target, char left, long[] a, char right) throws IOException {
        if (a != null) {        
            int iMax = a.length - 1;
            if (iMax == -1) {
                target.append("{}");
                return target;
            }
            target.append(left);
            for (int i = 0; ; i++) {
                appendValue(target,a[i]);
                //target.append(Long.toString(a[i]));
                if (i == iMax)
                    return (A) target.append(right);
                target.append(", ");
            } 
        } else {
            target.append("null");
        
        }
        return target;
    }

    public static <A extends Appendable> A appendArray(A target, char left, int[] a, char right) throws IOException {
        if (a != null) {        
            int iMax = a.length - 1;
            if (iMax == -1) {
                target.append("{}");
                return target;
            }
            target.append(left);
            for (int i = 0; ; i++) {
                appendValue(target,a[i]);
                //target.append(Integer.toString(a[i]));
                if (i == iMax)
                    return (A) target.append(right);
                target.append(", ");
            } 
        } else {
            target.append("null");
        
        }
        return target;
    }

    public static <A extends Appendable> A appendValue(A target, int value) throws IOException {

        int tens = 1000000000;
        
        if (value<0) {
            target.append('-');
            value = -value;
        }
        
        int nextValue = value;
        int orAll = 0; //this is to remove the leading zeros
        while (tens>1) {
            int digit = nextValue/tens;
            orAll |= digit;
            if (0!=orAll) {
                target.append((char)('0'+digit));
            }
            nextValue = nextValue%tens;
            tens /= 10;
        }
        target.append((char)('0'+nextValue));
        
        return target;
    }
    
    public static <A extends Appendable> A appendHexDigits(A target, int value) throws IOException {

        int bits = 32 - Integer.numberOfLeadingZeros(value);
        
        target.append("0x");
        int nextValue = value;
        int orAll = 0; //this is to remove the leading zeros
        while (bits>4) {
            bits -= 4;            
            int digit = nextValue>>bits;
            orAll |= digit;
            if (0!=orAll) {
                target.append(hBase[digit]);            
            }
            nextValue =  ((1<<bits)-1) & nextValue;
        }
        bits -= 4;
        target.append(hBase[nextValue>>bits]);
        
        return target;
    }
    
    public static <A extends Appendable> A appendValue(A target, long value) throws IOException {

        long tens = 1000000000000000000L;
        
        if (value<0) {
            target.append('-');
            value = -value;
        }
        
        long nextValue = value;
        int orAll = 0; //this is to remove the leading zeros
        while (tens>1) {
            int digit = (int)(nextValue/tens);
            orAll |= digit;
            if (0!=orAll) {
                target.append((char)('0'+digit));
            }
            nextValue = nextValue%tens;
            tens /= 10;
        }
        target.append((char)('0'+nextValue));
        
        return target;
    }
    
    public static <A extends Appendable> A appendHexDigits(A target, long value) throws IOException {

        
        
        int bits = 64-Long.numberOfLeadingZeros(value);
        
        target.append("0x");
        long nextValue = value;
        int orAll = 0; //this is to remove the leading zeros
        while (bits>4) {
            bits -= 4;            
            int digit = (int)nextValue>>bits;
            orAll |= digit;
            if (0!=orAll) {
                target.append(hBase[digit]);            
            }
            nextValue =  ((1<<bits)-1) & nextValue;
        }
        bits -= 4;
        target.append(hBase[(int)(nextValue>>bits)]);
        
        return target;
    }

    /*
     * 
     * In order to render a number like 42 with exactly 2 places the tests argument must be set to 10, likewise 042 would require 100
     */
    public static <A extends Appendable> A appendFixedDecimalDigits(A target, int value, int tens) throws IOException {

        if (value<0) {
            target.append('-');
            value = -value;
        }
        
        int nextValue = value;
        while (tens>1) {
            target.append((char)('0'+(nextValue/tens)));
            nextValue = nextValue%tens;
            tens /= 10;
        }
        target.append((char)('0'+nextValue));
        
        return target;
    }
    
    /*
     * 
     * In order to render an 8 bit number the bits must be set to 8. note that bits can only be in units of 4.
     */
    public static <A extends Appendable> A appendFixedHexDigits(A target, int value, int bits) throws IOException {

        target.append("0x");
        int nextValue = value;
        while (bits>4) {            
            bits -= 4;
            target.append(hBase[nextValue>>bits]);            
            nextValue =  ((1<<bits)-1) & nextValue;
        }
        bits -= 4;
        target.append(hBase[nextValue>>bits]);
        
        return target;
    }
    
    public static <A extends Appendable> A appendClass(A target, Class clazz, Class clazzParam) throws IOException {
        return (A) target.append(clazz.getSimpleName()).append('<').append(clazzParam.getSimpleName()).append("> ");
    }
    
}
