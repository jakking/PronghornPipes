package com.ociweb.pronghorn.ring;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.pronghorn.ring.token.OperatorMask;
import com.ociweb.pronghorn.ring.token.TokenBuilder;
import com.ociweb.pronghorn.ring.token.TypeMask;

public class RingWalker {
	private static final Logger log = LoggerFactory.getLogger(RingWalker.class);
	public final FieldReferenceOffsetManager from;

	int msgIdx=-1;
    int msgIdxPrev =-1; //for debug
    boolean isNewMessage;
    public int cursor;
            
    //These two fields are holding state for the high level API
    public long nextWorkingTail; //This is NOT the same as the low level tail cache, this is for reading side of the ring
    public long nextWorkingHead; //This is NOT the same as the low level head cache, this is for writing side of the ring

    public long holdingNextWorkingTail; 
    public long holdingNextWorkingHead;

    
    //TODO: AA, need to add error checking to caputre the case when something on the stack has fallen off the ring
    //      This can be a simple assert when we move the tail that it does not go past the value in stack[0];
    
	final long[] activeReadFragmentStack;
	final long[] activeWriteFragmentStack; 
	private final int[] seqStack;
	private final int[] seqCursors;
	int seqStackHead; //TODO: convert to private
	
		
	int nextCursor = -1;
    
	
	RingWalker(FieldReferenceOffsetManager from) {
		
		    if (null==from) {
	            throw new UnsupportedOperationException();
	        }
	        this.msgIdx = -1;
	        this.isNewMessage = false;
	        this.cursor = -1;
	        this.seqStack = new int[from.maximumFragmentStackDepth];
	        this.seqCursors = new int[from.maximumFragmentStackDepth];
	        this.seqStackHead = -1;
	        this.from = from;
	        this.activeWriteFragmentStack = new long[from.maximumFragmentStackDepth];
	        this.activeReadFragmentStack = new long[from.maximumFragmentStackDepth];
	}
	


    static void setMsgIdx(RingWalker rw, int idx, long llwHeadPosCache) {
		rw.msgIdxPrev = rw.msgIdx;
		//rw.log.trace("set message id {}", idx);
		rw.msgIdx = idx;
		assert(isMsgIdxStartNewMessage(idx, rw)) : "Bad msgIdx is not a starting point. ";
		assert(idx>-3);
		
		//This validation is very important, because all down stream consumers will assume it to be true.
		assert(-1 ==idx || (rw.from.hasSimpleMessagesOnly && 0==rw.msgIdx && rw.from.messageStarts.length==1)  ||
				TypeMask.Group == TokenBuilder.extractType(rw.from.tokens[rw.msgIdx])) :
					errorMessageForMessageStartValidation(rw, llwHeadPosCache);
		assert(-1 ==idx || (rw.from.hasSimpleMessagesOnly && 0==rw.msgIdx && rw.from.messageStarts.length==1)  ||
				(OperatorMask.Group_Bit_Close&TokenBuilder.extractOper(rw.from.tokens[rw.msgIdx])) == 0) :
					errorMessageForMessageStartValidation(rw, llwHeadPosCache);
			
		
	}


    private static String errorMessageForMessageStartValidation(RingWalker rw,
            long llwHeadPosCache) {
        return "Templated message must start with group open and this starts with "+TokenBuilder.tokenToString(rw.from.tokens[rw.msgIdx])+ 
        " readBase "+rw.activeReadFragmentStack[0] + " nextWorkingTail:"+rw.nextWorkingTail+" headPosCache:"+llwHeadPosCache;
    }


    //TODO: may want to move preamble to after the ID, it may be easier to reason about.
    
    static boolean prepReadFragment(RingBuffer ringBuffer,
			final RingWalker ringBufferConsumer) {
		//this fragment does not start a message
		ringBufferConsumer.isNewMessage = false;
		
		///
		//check the ring buffer looking for full next fragment
		//return false if we don't have enough data 
		ringBufferConsumer.cursor = ringBufferConsumer.nextCursor; //TODO: for nested example nextCursor must be 35 NOT 26 when we reach the end of the sequence.
		
		assert(isValidFragmentStart(ringBuffer, ringBufferConsumer.nextWorkingTail)) : "last assigned fragment start is invalid, should have been detected far before this point.";
		
		final long target = ringBufferConsumer.from.fragDataSize[ringBufferConsumer.cursor] + ringBufferConsumer.nextWorkingTail; //One for the template ID NOTE: Caution, this simple implementation does NOT support preamble
		
		assert(isValidFragmentStart(ringBuffer, target)) : invalidFragmentStartMessage(ringBufferConsumer, target);
				
		if (ringBuffer.llWrite.llwHeadPosCache >= target) {
			prepReadFragment(ringBuffer, ringBufferConsumer, ringBufferConsumer.from.fragScriptSize[ringBufferConsumer.cursor], ringBufferConsumer.nextWorkingTail, target);
		} else {
			//only update the cache with this CAS call if we are still waiting for data
			if ((ringBuffer.llWrite.llwHeadPosCache = RingBuffer.headPosition(ringBuffer)) >= target) {
				prepReadFragment(ringBuffer, ringBufferConsumer, ringBufferConsumer.from.fragScriptSize[ringBufferConsumer.cursor], ringBufferConsumer.nextWorkingTail, target);
			} else {
				ringBufferConsumer.isNewMessage = false; 
								
				assert (ringBuffer.llWrite.llwHeadPosCache<=ringBufferConsumer.nextWorkingTail) : 
					  "Partial fragment published!  expected "+(target-ringBufferConsumer.nextWorkingTail)+" but found "+(ringBuffer.llWrite.llwHeadPosCache-ringBufferConsumer.nextWorkingTail);

				return false;
			}
		}
		return true;
	}



    private static String invalidFragmentStartMessage(
            final RingWalker ringBufferConsumer, final long target) {
        return "Bad target of "+target+" for new fragment start. cursor:"+ringBufferConsumer.cursor+" name:"+ringBufferConsumer.from.fieldNameScript[ringBufferConsumer.cursor] +"    X="+ringBufferConsumer.from.fragDataSize[ringBufferConsumer.cursor]+"+"+ ringBufferConsumer.nextWorkingTail;
    }


	static boolean prepReadMessage(RingBuffer ringBuffer, RingWalker ringBufferConsumer) {
	
		///
		//check the ring buffer looking for new message	
		//return false if we don't have enough data to read the first id and therefore the message
		if (ringBuffer.llWrite.llwHeadPosCache > 1+ringBufferConsumer.nextWorkingTail) { 
			prepReadMessage(ringBuffer, ringBufferConsumer, ringBufferConsumer.nextWorkingTail);
		} else {
			//only update the cache with this CAS call if we are still waiting for data
			if ((ringBuffer.llWrite.llwHeadPosCache = RingBuffer.headPosition(ringBuffer)) > 1+ringBufferConsumer.nextWorkingTail) {
				prepReadMessage(ringBuffer, ringBufferConsumer, ringBufferConsumer.nextWorkingTail);
			} else {
				//rare slow case where we dont find any data
				ringBufferConsumer.isNewMessage = false; 
				return false;					
			}
		}
      return true;//exit early because we do not have any nested closed groups to check for
	}


	private static void prepReadFragment(RingBuffer ringBuffer,
			final RingWalker ringBufferConsumer, final int scriptFragSize,
			long tmpNextWokingTail, final long target) {

		//always increment this tail position by the count of bytes used by this fragment
		RingBuffer.addAndGetBytesWorkingTailPosition(ringBuffer, RingBuffer.primaryBuffer(ringBuffer)[ringBuffer.mask & (int)(tmpNextWokingTail-1)]);			


		//from the last known fragment move up the working tail position to this new fragment location
		RingBuffer.setWorkingTailPosition(ringBuffer, tmpNextWokingTail);
		
		//save the index into these fragments so the reader will be able to find them.
		ringBufferConsumer.activeReadFragmentStack[ringBufferConsumer.from.fragDepth[ringBufferConsumer.cursor]] =tmpNextWokingTail;
		
		assert(RingBuffer.bytesWorkingTailPosition(ringBuffer) <= RingBuffer.bytesHeadPosition(ringBuffer)) : "expected to have data up to "+RingBuffer.bytesWorkingTailPosition(ringBuffer)+" but we only have "+RingBuffer.bytesHeadPosition(ringBuffer);
		
		if ((RingBuffer.decBatchRelease(ringBuffer)<=0)) {	
			
			RingBuffer.releaseReadLock2(ringBuffer);
		}

		int lastScriptPos = (ringBufferConsumer.nextCursor = ringBufferConsumer.cursor + scriptFragSize) -1;
		prepReadFragment2(ringBuffer, ringBufferConsumer, tmpNextWokingTail, target, lastScriptPos, ringBufferConsumer.from.tokens[lastScriptPos]);	
	

	}


	private static void prepReadFragment2(RingBuffer ringBuffer,
			final RingWalker ringBufferConsumer, long tmpNextWokingTail,
			final long target, int lastScriptPos, int lastTokenOfFragment) {
	    
	    assert(isValidFragmentStart(ringBuffer, target)) : "Bad target of "+target+" for new fragment start";
	    
		//                                   11011    must not equal               10000
        if ( (lastTokenOfFragment &  ( 0x1B <<TokenBuilder.SHIFT_TYPE)) != ( 0x10<<TokenBuilder.SHIFT_TYPE ) ) {
        	 ringBufferConsumer.nextWorkingTail = target;//save the size of this new fragment we are about to read 
        } else {
        	 openOrCloseSequenceWhileInsideFragment(ringBuffer,	ringBufferConsumer, tmpNextWokingTail, lastScriptPos, lastTokenOfFragment);
        	 ringBufferConsumer.nextWorkingTail = target;
        }
        
	}


	private static boolean isValidFragmentStart(RingBuffer ringBuffer, long newFragmentBegin) {
        
	    if (newFragmentBegin<=0) {
	        return true;
	    }
	    //this fragment must not be after the head write position
	    if (newFragmentBegin>RingBuffer.headPosition(ringBuffer)) {
	        FieldReferenceOffsetManager.debugFROM(RingBuffer.from(ringBuffer));
	        log.error("new fragment to read is after head write position "+newFragmentBegin+"  "+ringBuffer);	        
	        int start = Math.max(0, ringBuffer.mask&(int)newFragmentBegin-5);
	        int stop  = Math.min(RingBuffer.primaryBuffer(ringBuffer).length, ringBuffer.mask&(int)newFragmentBegin+5);
	        log.error("Buffer from {} is {}",start, Arrays.toString(Arrays.copyOfRange(RingBuffer.primaryBuffer(ringBuffer), start, stop)));
	        return false;
	    }
	    if (newFragmentBegin>0) {
	        int byteCount = RingBuffer.primaryBuffer(ringBuffer)[ringBuffer.mask & (int)(newFragmentBegin-1)];
	        if (byteCount<0) {
	            log.error("if this is a new fragment then previous fragment is negative byte count, more likely this is NOT a valid fragment start.");
	            int start = Math.max(0, ringBuffer.mask&(int)newFragmentBegin-5);
	            int stop  = Math.min(RingBuffer.primaryBuffer(ringBuffer).length, ringBuffer.mask&(int)newFragmentBegin+5);
	            log.error("Buffer from {} is {}",start, Arrays.toString(Arrays.copyOfRange(RingBuffer.primaryBuffer(ringBuffer), start, stop)));
	            return false;
	        }
            if (byteCount>ringBuffer.byteMask) {
                log.error("if this is a new fragment then previous fragment byte count is larger than byte buffer, more likely this is NOT a valid fragment start. "+byteCount);
                int start = Math.max(0, ringBuffer.mask&(int)newFragmentBegin-5);
                int stop  = Math.min(RingBuffer.primaryBuffer(ringBuffer).length, ringBuffer.mask&(int)newFragmentBegin+5);
                log.error("Buffer from {} is {}",start, Arrays.toString(Arrays.copyOfRange(RingBuffer.primaryBuffer(ringBuffer), start, stop)));
                return false;
            }	        
	    }
        return true;
    }


    private static void openOrCloseSequenceWhileInsideFragment(
			RingBuffer ringBuffer, final RingWalker ringBufferConsumer,
			long tmpNextWokingTail, int lastScriptPos, int lastTokenOfFragment) {
		//this is a group or groupLength that has appeared while inside a fragment that does not start a message

		 //this single bit on indicates that this starts a sequence length  00100
		 if ( (lastTokenOfFragment &  ( 0x04 <<TokenBuilder.SHIFT_TYPE)) != 0 ) {
			 //this is a groupLength Sequence that starts inside of a fragment 
			 int seqLength = RingBuffer.primaryBuffer(ringBuffer)[(int)(ringBufferConsumer.from.fragDataSize[lastScriptPos] + tmpNextWokingTail)&ringBuffer.mask];
			 //now start new sequence
             ringBufferConsumer.seqStack[++ringBufferConsumer.seqStackHead] = seqLength;
             ringBufferConsumer.seqCursors[ringBufferConsumer.seqStackHead] = ringBufferConsumer.nextCursor;
		 } else {
    	     if (//if this is a closing sequence group.
    				 (lastTokenOfFragment & ( (OperatorMask.Group_Bit_Seq|OperatorMask.Group_Bit_Close) <<TokenBuilder.SHIFT_OPER)) == ((OperatorMask.Group_Bit_Seq|OperatorMask.Group_Bit_Close)<<TokenBuilder.SHIFT_OPER)          
    	            ) {    	         
    	                continueSequence(ringBufferConsumer);
    		        }
		 }
		 
	}


    private static boolean isClosingSequence(int token) {
        return (TypeMask.Group==((token >>> TokenBuilder.SHIFT_TYPE) & TokenBuilder.MASK_TYPE)) && 
               (0 != (token & (OperatorMask.Group_Bit_Close << TokenBuilder.SHIFT_OPER))) &&
               (0 != (token & (OperatorMask.Group_Bit_Seq << TokenBuilder.SHIFT_OPER)));
    }

	//only called when a closing sequence group is hit.
	private static void continueSequence(final RingWalker ringBufferConsumer) {
		//check top of the stack	    
	    
		if (--ringBufferConsumer.seqStack[ringBufferConsumer.seqStackHead]>0) {		            	
			//stay on cursor location we are counting down.
		    ringBufferConsumer.nextCursor = ringBufferConsumer.seqCursors[ringBufferConsumer.seqStackHead];
		   // System.err.println("ENDING WITHsss :"+ringBufferConsumer.nextCursor);
		    
		} else {
			closeSequence(ringBufferConsumer);
		}
	}


   //TODO:AAAA, must auto call this on sequence of zero to close the sequence the nextCursor must be set to end not begginning.
	
    private static void closeSequence(final RingWalker ringBufferConsumer) {
        if (--ringBufferConsumer.seqStackHead>=0) {
            if (isClosingSequence(ringBufferConsumer.from.tokens[ringBufferConsumer.nextCursor ])) {
                if (--ringBufferConsumer.seqStack[ringBufferConsumer.seqStackHead]>0) {  
                    ringBufferConsumer.nextCursor = ringBufferConsumer.seqCursors[ringBufferConsumer.seqStackHead];
                } else {
                    --ringBufferConsumer.seqStackHead;//this dec is the same as the one in the above conditional
                    //TODO: A, Note this repeating pattern above, this supports 2 nested sequences, Rewrite as while loop to support any number of nested sequences.
                    ringBufferConsumer.nextCursor++;
                }
            }
        } else {
            assert(ringBufferConsumer.seqStackHead<0) : "Error the seqStack should be empty but found value at "+ringBufferConsumer.seqStackHead;
            ringBufferConsumer.nextCursor++;
        }
    }


	private static void prepReadMessage(RingBuffer ringBuffer, RingWalker ringBufferConsumer, long tmpNextWokingTail) {
		//we now have enough room to read the id
		//for this simple case we always have a new message
		ringBufferConsumer.isNewMessage = true; 
		
		//always increment this tail position by the count of bytes used by this fragment
		if (tmpNextWokingTail>0) { //first iteration it will not have a valid position
		    int bytesConsumed = RingBuffer.primaryBuffer(ringBuffer)[ringBuffer.mask & (int)(tmpNextWokingTail-1)];
		    assert(bytesConsumed>=0 && bytesConsumed<=RingBuffer.byteBuffer(ringBuffer).length) : "bad byte count at "+(tmpNextWokingTail-1);
		    //System.err.println("bytes consumed :"+bytesConsumed);
		    
		    
		    RingBuffer.addAndGetBytesWorkingTailPosition(ringBuffer, bytesConsumed);
		}	

		//the byteWorkingTail now holds the new base
		RingBuffer.markBytesReadBase(ringBuffer);
		
		//
		//batched release of the old positions back to the producer
		//could be done every time but batching reduces contention
		//this batching is only done per-message so the fragments can remain and be read
		if ((RingBuffer.decBatchRelease(ringBuffer)>0)) {	
			//from the last known fragment move up the working tail position to this new fragment location
		    RingBuffer.setWorkingTailPosition(ringBuffer, ringBufferConsumer.nextWorkingTail);
		    
		} else {			
			RingBuffer.releaseReadLock2(ringBuffer);
		}
		prepReadMessage2(ringBuffer, ringBufferConsumer, tmpNextWokingTail);

		
	}


	private static boolean isMsgIdxStartNewMessage(int msgIdx, RingWalker ringBufferConsumer) {
        if (msgIdx<0) {
            return true;
        }
	    
	    int[] starts = ringBufferConsumer.from.messageStarts;
	    int i = starts.length;
	    while (--i>=0) {
	        if (starts[i]==msgIdx) {
	            return true;
	        }
	    }
	    
	    System.err.println("bad cursor "+msgIdx+" expected one of "+Arrays.toString(starts));	    
	    return false;
    }


    private static void prepReadMessage2(RingBuffer ringBuffer, RingWalker ringBufferConsumer, final long tmpNextWokingTail) {
		//
		//Start new stack of fragments because this is a new message
		ringBufferConsumer.activeReadFragmentStack[0] = tmpNextWokingTail;				 
		setMsgIdx(ringBufferConsumer, readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail), ringBuffer.llWrite.llwHeadPosCache);
		prepReadMessage2(ringBuffer, ringBufferConsumer, tmpNextWokingTail,	ringBufferConsumer.from.fragDataSize);
	}


	private static int readMsgIdx(RingBuffer ringBuffer, RingWalker ringBufferConsumer, final long tmpNextWokingTail) {
	    
		int i = ringBuffer.mask & (int)(tmpNextWokingTail + ringBufferConsumer.from.templateOffset);
        int idx = RingBuffer.primaryBuffer(ringBuffer)[i];
        
		
		assert(isMsgIdxStartNewMessage(idx, ringBufferConsumer)) : "Bad msgIdx is not a starting point.";
		return idx;
	}


	private static void prepReadMessage2(RingBuffer ringBuffer,	RingWalker ringBufferConsumer, final long tmpNextWokingTail, int[] fragDataSize) {
		if (ringBufferConsumer.msgIdx >= 0 && ringBufferConsumer.msgIdx < fragDataSize.length) {
			prepReadMessage2Normal(ringBuffer, ringBufferConsumer, tmpNextWokingTail, fragDataSize); 
		} else {
			prepReadMessage2EOF(ringBuffer, ringBufferConsumer, tmpNextWokingTail, fragDataSize);
		}
	}


	private static void prepReadMessage2Normal(RingBuffer ringBuffer,
			RingWalker ringBufferConsumer, final long tmpNextWokingTail,
			int[] fragDataSize) {

		ringBufferConsumer.nextWorkingTail = tmpNextWokingTail + fragDataSize[ringBufferConsumer.msgIdx];//save the size of this new fragment we are about to read  
		
		
		//This validation is very important, because all down stream consumers will assume it to be true.
		assert((ringBufferConsumer.from.hasSimpleMessagesOnly && 0==readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail) && ringBufferConsumer.from.messageStarts.length==1)  ||
				TypeMask.Group == TokenBuilder.extractType(ringBufferConsumer.from.tokens[readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail)])) : "Templated message must start with group open and this starts with "+TokenBuilder.tokenToString(ringBufferConsumer.from.tokens[readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail)]);
	
		assert((ringBufferConsumer.from.hasSimpleMessagesOnly && 0==readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail) && ringBufferConsumer.from.messageStarts.length==1)  ||
				(OperatorMask.Group_Bit_Close&TokenBuilder.extractOper(ringBufferConsumer.from.tokens[readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail)])) == 0) : "Templated message must start with group open and this starts with "+TokenBuilder.tokenToString(ringBufferConsumer.from.tokens[readMsgIdx(ringBuffer, ringBufferConsumer, tmpNextWokingTail)]);
	
		ringBufferConsumer.cursor = ringBufferConsumer.msgIdx;  
		
		int lastScriptPos = (ringBufferConsumer.nextCursor = ringBufferConsumer.msgIdx + ringBufferConsumer.from.fragScriptSize[ringBufferConsumer.msgIdx]) -1;
		if (TypeMask.GroupLength == ((ringBufferConsumer.from.tokens[lastScriptPos] >>> TokenBuilder.SHIFT_TYPE) & TokenBuilder.MASK_TYPE)) {
			//Can not assume end of message any more.
			int seqLength = RingBuffer.primaryBuffer(ringBuffer)[(int)(ringBufferConsumer.from.fragDataSize[lastScriptPos] + tmpNextWokingTail)&ringBuffer.mask];
            final RingWalker ringBufferConsumer1 = ringBufferConsumer;
			//now start new sequence
            ringBufferConsumer1.seqStack[++ringBufferConsumer1.seqStackHead] = seqLength;
            ringBufferConsumer1.seqCursors[ringBufferConsumer1.seqStackHead] = ringBufferConsumer1.nextCursor;
		}
	}

	private static void prepReadMessage2EOF(RingBuffer ringBuffer,
			RingWalker ringBufferConsumer, final long tmpNextWokingTail,
			int[] fragDataSize) {
		//rare so we can afford some extra checking at this point 
		if (ringBufferConsumer.msgIdx > fragDataSize.length) {
		    
		    
		    
			//this is very large so it is probably bad data, catch it now and send back a meaningful error
			int limit = (ringBuffer.mask & (int)(tmpNextWokingTail + ringBufferConsumer.from.templateOffset))+1;
			throw new UnsupportedOperationException("Bad msgId:"+ringBufferConsumer.msgIdx+
					" encountered at last absolute position:"+(tmpNextWokingTail + ringBufferConsumer.from.templateOffset)+
					" recent primary ring context:"+Arrays.toString( Arrays.copyOfRange(RingBuffer.primaryBuffer(ringBuffer), Math.max(0, limit-10), limit )));
		}		
		
		//this is commonly used as the end of file marker  
		ringBufferConsumer.nextWorkingTail = tmpNextWokingTail+RingBuffer.EOF_SIZE;
	}
    
 
	static void reset(RingWalker ringWalker, int ringPos) {

        /////
        resetCursorState(ringWalker);
        
        ringWalker.nextWorkingHead=ringPos;// reading position
        ringWalker.nextWorkingTail=ringPos;// writing position        
                
    }



    static void resetCursorState(RingWalker ringWalker) {
        ringWalker.cursor = -1;
        ringWalker.nextCursor = -1;
        ringWalker.seqStackHead = -1;
        ringWalker.msgIdxPrev = -1;
        ringWalker.msgIdx = -1;
        
        ringWalker.isNewMessage = false;
    }

	
	static boolean tryWriteFragment1(RingBuffer ring, int cursorPosition, FieldReferenceOffsetManager from, int fragSize,
											long target, boolean hasRoom) {
		//try again and update the cache with the newest value
		if (hasRoom) {
			prepWriteFragment(ring, cursorPosition, from, fragSize);
		} else {
			//only if there is no room should we hit the CAS tailPos and then try again.
			hasRoom = (ring.llRead.llrTailPosCache = RingBuffer.tailPosition(ring)) >=  target;		
			if (hasRoom) {		
				prepWriteFragment(ring, cursorPosition, from, fragSize);
			}
		}
			
		return hasRoom;
	}
	
	static void prepWriteFragment(RingBuffer ring, int cursorPosition,	FieldReferenceOffsetManager from, int fragSize) {
		//NOTE: this is called by both blockWrite and tryWrite.  It must not call publish because we need to support
		//      nested long sequences where we don't know the length until after they are all written.
		
		prepWriteFragmentSpecificProcessing(ring, cursorPosition, from);
		RingBuffer.addAndGetWorkingHead(ring, fragSize);
		ring.ringWalker.nextWorkingHead = ring.ringWalker.nextWorkingHead + fragSize;

		//when publish is called this new byte will be appended due to this request

				
	}


	private static void prepWriteFragmentSpecificProcessing(RingBuffer ring, int cursorPosition, FieldReferenceOffsetManager from) {
		
		if (FieldReferenceOffsetManager.isTemplateStart(from, cursorPosition)) {
			prepWriteMessageStart(ring, cursorPosition, from);
		 } else {			
			//this fragment does not start a new message but its start position must be recorded for usage later
			ring.ringWalker.activeWriteFragmentStack[from.fragDepth[cursorPosition]]=RingBuffer.workingHeadPosition(ring);
		 }
	}


	private static void prepWriteMessageStart(RingBuffer ring,
			int cursorPosition, FieldReferenceOffsetManager from) {
		//each time some bytes were written in the previous fragment this value was incremented.		
		//now it becomes the base value for all byte writes
		RingBuffer.markBytesWriteBase(ring);
		
		//Start new stack of fragments because this is a new message
		ring.ringWalker.activeWriteFragmentStack[0] = RingBuffer.workingHeadPosition(ring);
		RingBuffer.primaryBuffer(ring)[ring.mask &(int)(RingBuffer.workingHeadPosition(ring) + from.templateOffset)] = cursorPosition;
	}

	
	
	
	static boolean copyFragment0(RingBuffer inputRing, RingBuffer outputRing, long start, long end) {
		return copyFragment1(inputRing, outputRing, start, (int)(end-start), RingBuffer.primaryBuffer(inputRing)[inputRing.mask&(((int)end)-1)]);
	}


	private static boolean copyFragment1(RingBuffer inputRing, RingBuffer outputRing, long start, int spaceNeeded, int bytesToCopy) {
		
		if ((spaceNeeded >  outputRing.sizeOfStructuredLayoutRingBuffer-(int)(RingBuffer.workingHeadPosition(outputRing) - RingBuffer.tailPosition(outputRing)) )) {
			return false;
		}
		copyFragment2(inputRing, outputRing, (int)start, spaceNeeded, bytesToCopy);		
		return true;
	}


	private static void copyFragment2(RingBuffer inputRing,	RingBuffer outputRing, int start, int spaceNeeded, int bytesToCopy) {
		
		RingBuffer.copyIntsFromToRing(RingBuffer.primaryBuffer(inputRing), start, inputRing.mask, 
		                              RingBuffer.primaryBuffer(outputRing), (int)RingBuffer.workingHeadPosition(outputRing), outputRing.mask, 
				                      spaceNeeded);
		RingBuffer.addAndGetWorkingHead(outputRing, spaceNeeded);
		
		RingBuffer.copyBytesFromToRing(RingBuffer.byteBuffer(inputRing), RingBuffer.bytesWorkingTailPosition(inputRing), inputRing.byteMask, 
		                               RingBuffer.byteBuffer(outputRing), RingBuffer.bytesWorkingHeadPosition(outputRing), outputRing.byteMask, 
				                       bytesToCopy);

        RingBuffer.addAndGetBytesWorkingHeadPosition(outputRing, bytesToCopy);		
		
		//release the input fragment, we are using working tail and next working tail so batch release should work on walker.
		//prepReadFragment and tryReadFragment do the batched release, so it is not done here.
		
		//NOTE: writes are using low-level calls and we must publish them.
		RingBuffer.publishHeadPositions(outputRing); //we use the working head pos so batching still works here.
	}
	
	
	

   
}