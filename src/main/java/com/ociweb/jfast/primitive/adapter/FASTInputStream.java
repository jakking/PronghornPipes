package com.ociweb.jfast.primitive.adapter;

import java.io.IOException;
import java.io.InputStream;

import com.ociweb.jfast.primitive.FASTInput;
import com.ociweb.jfast.read.FASTException;

public class FASTInputStream implements FASTInput {

	private InputStream inst;
	
	public FASTInputStream(InputStream inst) {
		this.inst = inst;
	}
	
	public void replaceStream(InputStream inst) {
		this.inst = inst;
	}
	
	public int fill(byte[] buffer, int offset, int len) {
		try {
			int result = inst.read(buffer, offset, len);
			if (result<0) {
				return 0;
			}
			return result;
		} catch (IOException e) {
			throw new FASTException(e);
		}
	}
	
}