package com.ociweb.pronghorn.pipe.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Random;

import org.junit.Test;

import com.ociweb.pronghorn.util.Appendables;

public class AppendablesTest {

    private static int TEST_SIZE = 1000000;

    @Test
    public void appendIntDecimalValue() {
        
        Random r = new Random(101);
        
        for(int i = 0; i<TEST_SIZE; i++) {
            int value = r.nextInt();
            
                String actual = Appendables.appendValue(new StringBuilder(), value).toString();
                String expected = Integer.toString(value);
                if (value<0) {
                    expected = "("+expected+")";
                }
                assertEquals(expected, actual);
         
        }
        
    }

    @Test
    public void appendIntHexValue() {
        
        Random r = new Random(101);
        
        for(int i = 0; i<TEST_SIZE; i++) {
            int value = r.nextInt();
            
                String actual = Appendables.appendHexDigits(new StringBuilder(), value).toString().toLowerCase();
                String expected = "0x"+Integer.toHexString(value);
                assertEquals(""+i,expected, actual);
         
        }
    }  
    
    
    @Test
    public void appendIntHexFixedValue() {
        
        Random r = new Random(101);
        
        for(int i = 0; i<TEST_SIZE; i++) {
            int value = r.nextInt();
 
                String actual = Appendables.appendFixedHexDigits(new StringBuilder(), value, 32).toString().toLowerCase();
                String temp = "00000000"+Integer.toHexString(value);
                String expected = "0x"+temp.substring(temp.length()-8);
                assertEquals(""+i,expected, actual);                
                          
        }
    }  
    
    @Test
    public void appendSkipValue() {
        
        String originalText = "abcXYZdefXYyghiXklXYZ";
        String skipText = "XYZ";
        
        StringBuilder target = new StringBuilder();
        Appendables.appendAndSkip(target, originalText, skipText);

        
        String expected = originalText.replace(skipText,"");
        
        assertEquals(expected, target.toString());
                
    }
    
    
    
}
