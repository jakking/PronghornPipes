package com.ociweb.jfast.field;

import com.ociweb.jfast.primitive.PrimitiveReader;

public class FieldReaderChar {

	private static final int INIT_VALUE_MASK = 0x80000000;
	private final PrimitiveReader reader;
	private final TextHeap charDictionary;
	private final int INSTANCE_MASK;
	
	public FieldReaderChar(PrimitiveReader reader, TextHeap charDictionary) {
		
		assert(charDictionary.textCount()<TokenBuilder.MAX_INSTANCE);
		assert(isPowerOfTwo(charDictionary.textCount()));
		
		this.INSTANCE_MASK = (charDictionary.textCount()-1);
		
		this.reader = reader;
		this.charDictionary = charDictionary;
	}
	
	public TextHeap textHeap() {
		return charDictionary;
	}
	
	static boolean isPowerOfTwo(int length) {
		
		while (0==(length&1)) {
			length = length>>1;
		}
		return length==1;
	}

	final byte NULL_STOP = (byte)0x80;

	public int readASCIICopy(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()!=0) {
			readASCIIToHeap(idx);
		}
		return idx;
	}

	
	private void readASCIIToHeap(int idx) {
		
		// 0x80 is a null string.
		// 0x00, 0x80 is zero length string
		byte val = reader.readTextASCIIByte();
		if (val==0) {
			charDictionary.setZeroLength(idx);
			//must move cursor off the second byte
			val = reader.readTextASCIIByte();
			//at least do a validation because we already have what we need
			assert((val&0xFF)==0x80);
		} else {
			if (val==NULL_STOP) {
				charDictionary.setNull(idx);				
			} else {
				charDictionary.setZeroLength(idx);				
				fastHeapAppend(idx, val);
			}
		}
	}

	private void fastHeapAppend(int idx, byte val) {
		int offset = charDictionary.offset(idx);
		int nextLimit = charDictionary.nextLimit(offset);
		int targIndex = charDictionary.stopIndex(offset);
		
		char[] targ = charDictionary.rawAccess();
		
		while (val>=0) { 
	///		System.err.println("read val:"+((char)val));
				if (targIndex >= nextLimit) {
					charDictionary.makeSpaceForAppend(offset, 1);
					nextLimit = charDictionary.nextLimit(offset);
				}
				targ[targIndex++] = (char)val;
				val = reader.readTextASCIIByte();
		}
		if (targIndex >= nextLimit) {
			charDictionary.makeSpaceForAppend(offset, 1);
			nextLimit = charDictionary.nextLimit(offset);
		}	
	///	System.err.println("read val:"+((char)val));
		targ[targIndex++] = (char)(0x7F & val);
		charDictionary.stopIndex(offset,targIndex);
	//	System.err.println("new value:"+charDictionary.get(idx,new StringBuffer()));
	}
	
	

	public int readASCIIConstant(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			readASCIIToHeap(idx);
			return idx;
		}
	}
	
	public int readASCIIDefault(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			readASCIIToHeap(idx);
			return idx;
		}
	}
	
	public int readASCIIDefaultOptional(int token) {
		//for ASCII we don't need special behavior for optional
		return readASCIIDefault(token);
	}

	public int readASCIIDelta(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		
		if (trim>=0) {
			charDictionary.trimTail(idx, trim);
		} else {
			charDictionary.trimHead(idx, -trim);
		}
		
		byte value = reader.readTextASCIIByte();
		int offset = charDictionary.offset(idx);
		int nextLimit = charDictionary.nextLimit(offset);
		while (value>=0) {
			//TODO: this should be append head for head?
			nextLimit = charDictionary.appendTail(offset, nextLimit, (char)value);
			value = reader.readTextASCIIByte();
		}
		charDictionary.appendTail(offset, nextLimit, (char)(value&0x7F) );
				
		return idx;
	}

	public int readASCIITail(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerUnsigned();
		if (trim>0) {
			charDictionary.trimTail(idx, trim);
		}
		
		//System.err.println("read: trim "+trim);
		
		byte val = reader.readTextASCIIByte();
		if (val==0) {
			//nothing to append
			//must move cursor off the second byte
			val = reader.readTextASCIIByte();
			//at least do a validation because we already have what we need
			assert((val&0xFF)==0x80);
		} else {
			if (val==NULL_STOP) {
				//nothing to append
				//charDictionary.setNull(idx);				
			} else {		
				if (charDictionary.isNull(idx)) {
					charDictionary.setZeroLength(idx);
				}
				fastHeapAppend(idx, val);
			}
		}
		
		return idx;
	}
	

	public int readASCIITailOptional(int token) {
		int idx = token & INSTANCE_MASK;
		
		charDictionary.trimTail(idx, reader.readIntegerSigned());
		byte val = reader.readTextASCIIByte();
		if (val==0) {
			//nothing to append
			//must move cursor off the second byte
			val = reader.readTextASCIIByte();
			//at least do a validation because we already have what we need
			assert((val&0xFF)==0x80);
		} else {
			if (val==NULL_STOP) {
				//nothing to append
				//charDictionary.setNull(idx);				
			} else {		
				if (charDictionary.isNull(idx)) {
					charDictionary.setZeroLength(idx);
				}
				fastHeapAppend(idx, val);
			}
		}
						
		return idx;
	}

	public int readASCIICopyOptional(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()!=0) {
			readASCIIToHeap(idx);
		}
		return idx;
	}



	public int readASCIIDeltaOptional(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		
		if (trim>=0) {
			charDictionary.trimTail(idx, trim);
		} else {
			charDictionary.trimHead(idx, -trim);
		}
		
		byte value = reader.readTextASCIIByte();
		int offset = charDictionary.offset(idx);
		int nextLimit = charDictionary.nextLimit(offset);
		while (value>=0) {
			nextLimit = charDictionary.appendTail(offset, nextLimit, (char)value);
			value = reader.readTextASCIIByte();
		}
		charDictionary.appendTail(offset, nextLimit, (char)(value&0x7F) );
				
		return idx;
	}

	public int readUTF8Constant(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			
			int length = reader.readIntegerUnsigned();
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
						
			return idx;
		}
				
	}

	public int readUTF8Default(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			
			int length = reader.readIntegerUnsigned();
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
						
			return idx;
		}
	}
	

	public int readUTF8DefaultOptional(int token) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			
			int length = reader.readIntegerUnsigned()-1;
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
						
			return idx;
		}
	}

	public int readUTF8Delta(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		int utfLength = reader.readIntegerUnsigned();
		if (trim>=0) {
			//append to tail	
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForAppend(trim, idx, utfLength), utfLength);
		} else {
			//append to head
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForPrepend(trim, idx, utfLength), utfLength);
		}
		
		return idx;
	}

	public int readUTF8Tail(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		int utfLength = reader.readIntegerUnsigned(); 

		//append to tail	
		int targetOffset = charDictionary.makeSpaceForAppend(trim, idx, utfLength);
		
	//	int dif = charDictionary.length(idx);
		
	//	System.err.println("read: trim:"+trim+" utfLen:"+utfLength+" target:"+targetOffset+" made space "+dif);
		
		reader.readTextUTF8(charDictionary.rawAccess(), targetOffset, utfLength);
//		System.err.println("recv "+charDictionary.get(idx, new StringBuilder()));
		//TODO: this was written but why not found by get above??
//		System.err.println("recv "+new String(charDictionary.rawAccess(), targetOffset, utfLength));
		
		
		return idx;
	}
	
	public int readUTF8Copy(int token) {
		int idx = token & INSTANCE_MASK;
		if (reader.popPMapBit()!=0) {
			int length = reader.readIntegerUnsigned();
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
		}		
		return idx;
	}

	public int readUTF8CopyOptional(int token) {
		int idx = token & INSTANCE_MASK;
		if (reader.popPMapBit()!=0) {			
			int length = reader.readIntegerUnsigned()-1;
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
		}		
		return idx;
	}


	public int readUTF8DeltaOptional(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		int utfLength = reader.readIntegerUnsigned()-1; //subtract for optional
		if (trim>=0) {
			//append to tail	
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForAppend(trim, idx, utfLength), utfLength);
		} else {
			//append to head
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForPrepend(trim, idx, utfLength), utfLength);
		}
		
		return idx;
	}

	public int readUTF8TailOptional(int token) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		int utfLength = reader.readIntegerUnsigned()-1; //subtract for optional

		//append to tail	
		reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForAppend(trim, idx, utfLength), utfLength);
		
		return idx;
	}

	public int readASCII(int token) {
		int idx = token & INSTANCE_MASK;
		readASCIIToHeap(idx);
		return idx;
	}
	
	public int readTextASCIIOptional(int token) {
		return readASCII(token);
	}

	public int readUTF8(int token) {
		int idx = token & INSTANCE_MASK;
		int length = reader.readIntegerUnsigned();
		reader.readTextUTF8(charDictionary.rawAccess(), 
				            charDictionary.allocate(idx, length),
				            length);
		return idx;
	}

	public int readUTF8Optional(int token) {
		int idx = token & INSTANCE_MASK;
		int length = reader.readIntegerUnsigned()-1;
		reader.readTextUTF8(charDictionary.rawAccess(), 
				            charDictionary.allocate(idx, length),
				            length);
		return idx;
	}

	
}