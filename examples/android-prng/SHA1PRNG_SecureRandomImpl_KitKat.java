/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import com.galois.symbolic.Symbolic;

/**
 * This class extends the SecureRandomSpi class implementing all its abstract methods.
 *
 * <p>To generate pseudo-random bits, the implementation uses technique described in
 * the "Random Number Generator (RNG) algorithms" section, Appendix A,
 * JavaTM Cryptography Architecture, API Specification & Reference.
 */
public class SHA1PRNG_SecureRandomImpl_KitKat {

    /**
     *  constant defined in "SECURE HASH STANDARD"
     */
    public static final int H0 = 0x67452301;


    /**
     *  constant defined in "SECURE HASH STANDARD"
     */
    public static final int H1 = 0xEFCDAB89;


    /**
     *  constant defined in "SECURE HASH STANDARD"
     */
    public static final int H2 = 0x98BADCFE;


    /**
     *  constant defined in "SECURE HASH STANDARD"
     */
    public static final int H3 = 0x10325476;


    /**
     *  constant defined in "SECURE HASH STANDARD"
     */
    public static final int H4 = 0xC3D2E1F0;


    /**
     * offset in buffer to store number of bytes in 0-15 word frame
     */
    public static final int BYTES_OFFSET = 81;


    /**
     * offset in buffer to store current hash value
     */
    public static final int HASH_OFFSET = 82;


    /**
     * # of bytes in H0-H4 words; <BR>
     * in this implementation # is set to 20 (in general # varies from 1 to 20)
     */
    public static final int DIGEST_LENGTH = 20;

    private static final long serialVersionUID = 283736797212159675L;

    // constants to use in expressions operating on bytes in int and long variables:
    // END_FLAGS - final bytes in words to append to message;
    //             see "ch.5.1 Padding the Message, FIPS 180-2"
    // RIGHT1    - shifts to right for left half of long
    // RIGHT2    - shifts to right for right half of long
    // LEFT      - shifts to left for bytes
    // MASK      - mask to select counter's bytes after shift to right

    private static final int[] END_FLAGS = { 0x80000000, 0x800000, 0x8000, 0x80 };

    private static final int[] RIGHT1 = { 0, 40, 48, 56 };

    private static final int[] RIGHT2 = { 0, 8, 16, 24 };

    private static final int[] LEFT = { 0, 24, 16, 8 };

    private static final int[] MASK = { 0xFFFFFFFF, 0x00FFFFFF, 0x0000FFFF,
            0x000000FF };

    // HASHBYTES_TO_USE defines # of bytes returned by "computeHash(byte[])"
    // to use to form byte array returning by the "nextBytes(byte[])" method
    // Note, that this implementation uses more bytes than it is defined
    // in the above specification.
    private static final int HASHBYTES_TO_USE = 20;

    // value of 16 defined in the "SECURE HASH STANDARD", FIPS PUB 180-2
    private static final int FRAME_LENGTH = 16;

    // miscellaneous constants defined in this implementation:
    // COUNTER_BASE - initial value to set to "counter" before computing "nextBytes(..)";
    //                note, that the exact value is not defined in STANDARD
    // HASHCOPY_OFFSET   - offset for copy of current hash in "copies" array
    // EXTRAFRAME_OFFSET - offset for extra frame in "copies" array;
    //                     as the extra frame follows the current hash frame,
    //                     EXTRAFRAME_OFFSET is equal to length of current hash frame
    // FRAME_OFFSET      - offset for frame in "copies" array
    // MAX_BYTES - maximum # of seed bytes processing which doesn't require extra frame
    //             see (1) comments on usage of "seed" array below and
    //             (2) comments in "engineNextBytes(byte[])" method
    //
    // UNDEFINED  - three states of engine; initially its state is "UNDEFINED"
    // SET_SEED     call to "engineSetSeed"  sets up "SET_SEED" state,
    // NEXT_BYTES   call to "engineNextByte" sets up "NEXT_BYTES" state

    private static final int COUNTER_BASE = 0;

    private static final int HASHCOPY_OFFSET = 0;

    private static final int EXTRAFRAME_OFFSET = 5;

    private static final int FRAME_OFFSET = 21;

    private static final int MAX_BYTES = 48;

    private static final int UNDEFINED = 0;

    private static final int SET_SEED = 1;

    private static final int NEXT_BYTES = 2;

    // Structure of "seed" array:
    // -  0-79 - words for computing hash
    // - 80    - unused
    // - 81    - # of seed bytes in current seed frame
    // - 82-86 - 5 words, current seed hash
    private transient int[] seed;

    // total length of seed bytes, including all processed
    private transient long seedLength;

    // Structure of "copies" array
    // -  0-4  - 5 words, copy of current seed hash
    // -  5-20 - extra 16 words frame;
    //           is used if final padding exceeds 512-bit length
    // - 21-36 - 16 word frame to store a copy of remaining bytes
    private transient int[] copies;

    // ready "next" bytes; needed because words are returned
    private transient byte[] nextBytes;

    // index of used bytes in "nextBytes" array
    private transient int nextBIndex;

    // variable required according to "SECURE HASH STANDARD"
    private transient long counter;

    // contains int value corresponding to engine's current state
    private transient int state;

    // The "seed" array is used to compute both "current seed hash" and "next bytes".
    //
    // As the "SHA1" algorithm computes a hash of entire seed by splitting it into
    // a number of the 512-bit length frames (512 bits = 64 bytes = 16 words),
    // "current seed hash" is a hash (5 words, 20 bytes) for all previous full frames;
    // remaining bytes are stored in the 0-15 word frame of the "seed" array.
    //
    // As for calculating "next bytes",
    // both remaining bytes and "current seed hash" are used,
    // to preserve the latter for following "setSeed(..)" commands,
    // the following technique is used:
    // - upon getting "nextBytes(byte[])" invoked, single or first in row,
    //   which requires computing new hash, that is,
    //   there is no more bytes remaining from previous "next bytes" computation,
    //   remaining bytes are copied into the 21-36 word frame of the "copies" array;
    // - upon getting "setSeed(byte[])" invoked, single or first in row,
    //   remaining bytes are copied back.

    /**
     *  Creates object and sets implementation variables to their initial values
     */
    public SHA1PRNG_SecureRandomImpl_KitKat() {

        seed = new int[HASH_OFFSET + EXTRAFRAME_OFFSET];
        seed[HASH_OFFSET] = H0;
        seed[HASH_OFFSET + 1] = H1;
        seed[HASH_OFFSET + 2] = H2;
        seed[HASH_OFFSET + 3] = H3;
        seed[HASH_OFFSET + 4] = H4;

        seedLength = 0;
        copies = new int[2 * FRAME_LENGTH + EXTRAFRAME_OFFSET];
        nextBytes = new byte[DIGEST_LENGTH];
        nextBIndex = HASHBYTES_TO_USE;
        counter = COUNTER_BASE;
        state = UNDEFINED;
    }

    /*
     * The method invokes the SHA1Impl's "updateHash(..)" method
     * to update current seed frame and
     * to compute new intermediate hash value if the frame is full.
     *
     * After that it computes a length of whole seed.
     */
    private void updateSeed(byte[] bytes) {

        // on call:   "seed" contains current bytes and current hash;
        // on return: "seed" contains new current bytes and possibly new current hash
        //            if after adding, seed bytes overfill its buffer
        // TODO
        updateHash(seed, bytes, 0, bytes.length - 1);

        seedLength += bytes.length;
    }

    /**
     * Writes random bytes into an array supplied.
     * Bits in a byte are from left to right. <BR>
     *
     * To generate random bytes, the "expansion of source bits" method is used,
     * that is,
     * the current seed with a 64-bit counter appended is used to compute new bits.
     * The counter is incremented by 1 for each 20-byte output. <BR>
     *
     * The method overrides engineNextBytes in class SecureRandomSpi.
     *
     * @param
     *       bytes - byte array to be filled in with bytes
     * @throws
     *       NullPointerException - if null is passed to the "bytes" argument
     */
    protected void engineNextBytes(byte[] bytes) {

        int i, n;

        long bits; // number of bits required by Secure Hash Standard
        int nextByteToReturn; // index of ready bytes in "bytes" array
        int lastWord; // index of last word in frame containing bytes
        final int extrabytes = 7;// # of bytes to add in order to computer # of 8 byte words

        if (bytes == null) {
            throw new NullPointerException("bytes == null");
        }

        lastWord = seed[BYTES_OFFSET] == 0 ? 0
                : (seed[BYTES_OFFSET] + extrabytes) >> 3 - 1;

        if (state == UNDEFINED) {

            // no seed supplied by user, hence it is generated thus randomizing internal state
            updateSeed(Symbolic.freshByteArray(DIGEST_LENGTH));
            nextBIndex = HASHBYTES_TO_USE;

            // updateSeed(...) updates where the last word of the seed is, so we
            // have to read it again.
            lastWord = seed[BYTES_OFFSET] == 0 ? 0
                    : (seed[BYTES_OFFSET] + extrabytes) >> 3 - 1;

        } else if (state == SET_SEED) {

            System.arraycopy(seed, HASH_OFFSET, copies, HASHCOPY_OFFSET,
                    EXTRAFRAME_OFFSET);

            // possible cases for 64-byte frame:
            //
            // seed bytes < 48      - remaining bytes are enough for all, 8 counter bytes,
            //                        0x80, and 8 seedLength bytes; no extra frame required
            // 48 < seed bytes < 56 - remaining 9 bytes are for 0x80 and 8 counter bytes
            //                        extra frame contains only seedLength value at the end
            // seed bytes > 55      - extra frame contains both counter's bytes
            //                        at the beginning and seedLength value at the end;
            //                        note, that beginning extra bytes are not more than 8,
            //                        that is, only 2 extra words may be used

            // no need to set to "0" 3 words after "lastWord" and
            // more than two words behind frame
            for (i = lastWord + 3; i < FRAME_LENGTH + 2; i++) {
                seed[i] = 0;
            }

            bits = (seedLength << 3) + 64; // transforming # of bytes into # of bits

            // putting # of bits into two last words (14,15) of 16 word frame in
            // seed or copies array depending on total length after padding
            if (seed[BYTES_OFFSET] < MAX_BYTES) {
                seed[14] = (int) (bits >>> 32);
                seed[15] = (int) (bits & 0xFFFFFFFF);
            } else {
                copies[EXTRAFRAME_OFFSET + 14] = (int) (bits >>> 32);
                copies[EXTRAFRAME_OFFSET + 15] = (int) (bits & 0xFFFFFFFF);
            }

            nextBIndex = HASHBYTES_TO_USE; // skipping remaining random bits
        }
        state = NEXT_BYTES;

        if (bytes.length == 0) {
            return;
        }

        nextByteToReturn = 0;

        // possibly not all of HASHBYTES_TO_USE bytes were used previous time
        n = (HASHBYTES_TO_USE - nextBIndex) < (bytes.length - nextByteToReturn) ? HASHBYTES_TO_USE
                - nextBIndex
                : bytes.length - nextByteToReturn;
        if (n > 0) {
            System.arraycopy(nextBytes, nextBIndex, bytes, nextByteToReturn, n);
            nextBIndex += n;
            nextByteToReturn += n;
        }

        if (nextByteToReturn >= bytes.length) {
            return; // return because "bytes[]" are filled in
        }

        n = seed[BYTES_OFFSET] & 0x03;
        for (;;) {
            if (n == 0) {

                seed[lastWord] = (int) (counter >>> 32);
                seed[lastWord + 1] = (int) (counter & 0xFFFFFFFF);
                seed[lastWord + 2] = END_FLAGS[0];

            } else {

                seed[lastWord] |= (int) ((counter >>> RIGHT1[n]) & MASK[n]);
                seed[lastWord + 1] = (int) ((counter >>> RIGHT2[n]) & 0xFFFFFFFF);
                seed[lastWord + 2] = (int) ((counter << LEFT[n]) | END_FLAGS[n]);
            }
            if (seed[BYTES_OFFSET] > MAX_BYTES) {
                copies[EXTRAFRAME_OFFSET] = seed[FRAME_LENGTH];
                copies[EXTRAFRAME_OFFSET + 1] = seed[FRAME_LENGTH + 1];
            }

            // TODO
            //SHA1Impl.computeHash(seed);
            int[] seedInit = new int[5];
            int si;
            for (si = 0; si < 5; si++) { seedInit[si] = seed[si]; }
            Symbolic.writeAiger("kitkat_prng.aig", seedInit);

            if (seed[BYTES_OFFSET] > MAX_BYTES) {

                System.arraycopy(seed, 0, copies, FRAME_OFFSET, FRAME_LENGTH);
                System.arraycopy(copies, EXTRAFRAME_OFFSET, seed, 0,
                        FRAME_LENGTH);

                // TODO
                //SHA1Impl.computeHash(seed);
                System.arraycopy(copies, FRAME_OFFSET, seed, 0, FRAME_LENGTH);
            }
            counter++;

            int j = 0;
            for (i = 0; i < EXTRAFRAME_OFFSET; i++) {
                int k = seed[HASH_OFFSET + i];
                nextBytes[j] = (byte) (k >>> 24); // getting first  byte from left
                nextBytes[j + 1] = (byte) (k >>> 16); // getting second byte from left
                nextBytes[j + 2] = (byte) (k >>> 8); // getting third  byte from left
                nextBytes[j + 3] = (byte) (k); // getting fourth byte from left
                j += 4;
            }

            nextBIndex = 0;
            j = HASHBYTES_TO_USE < (bytes.length - nextByteToReturn) ? HASHBYTES_TO_USE
                    : bytes.length - nextByteToReturn;

            if (j > 0) {
                System.arraycopy(nextBytes, 0, bytes, nextByteToReturn, j);
                nextByteToReturn += j;
                nextBIndex += j;
            }

            if (nextByteToReturn >= bytes.length) {
                break;
            }
        }
    }

    /**
     * The method appends new bytes to existing ones
     * within limit of a frame of 64 bytes (16 words).
     *
     * Once a length of accumulated bytes reaches the limit
     * the "computeHash(int[])" method is invoked on the array to compute updated hash,
     * and the number of bytes in the frame is set to 0.
     * Thus, after appending all bytes, the array contain only those bytes
     * that were not used in computing final hash value yet.
     *
     * No checks on arguments passed to the method, that is,
     * a calling method is responsible for such checks.
     *
     * @params
     *        intArray  - int array containing bytes to which to append;
     *                    intArray.length >= (BYTES_OFFSET+6)
     * @params
     *        byteInput - array of bytes to use for the update
     * @params
     *        from      - the offset to start in the "byteInput" array
     * @params
     *        to        - a number of the last byte in the input array to use,
     *                that is, for first byte "to"==0, for last byte "to"==input.length-1
     */
    static void updateHash(int[] intArray, byte[] byteInput, int fromByte, int toByte) {

        // As intArray contains a packed bytes
        // the buffer's index is in the intArray[BYTES_OFFSET] element

        int index = intArray[BYTES_OFFSET];
        int i = fromByte;
        int maxWord;
        int nBytes;

        int wordIndex = index >>2;
        int byteIndex = index & 0x03;

        intArray[BYTES_OFFSET] = ( index + toByte - fromByte + 1 ) & 077 ;

        // In general case there are 3 stages :
        // - appending bytes to non-full word,
        // - writing 4 bytes into empty words,
        // - writing less than 4 bytes in last word

        if ( byteIndex != 0 ) {       // appending bytes in non-full word (as if)

            for ( ; ( i <= toByte ) && ( byteIndex < 4 ) ; i++ ) {
                intArray[wordIndex] |= ( byteInput[i] & 0xFF ) << ((3 - byteIndex)<<3) ;
                byteIndex++;
            }
            if ( byteIndex == 4 ) {
                wordIndex++;
                if ( wordIndex == 16 ) {          // intArray is full, computing hash

                    //computeHash(intArray);
                    wordIndex = 0;
                }
            }
            if ( i > toByte ) {                 // all input bytes appended
                return ;
            }
        }

        // writing full words

        maxWord = (toByte - i + 1) >> 2;           // # of remaining full words, may be "0"
        for ( int k = 0; k < maxWord ; k++ ) {

            intArray[wordIndex] = ( ((int) byteInput[i   ] & 0xFF) <<24 ) |
                                  ( ((int) byteInput[i +1] & 0xFF) <<16 ) |
                                  ( ((int) byteInput[i +2] & 0xFF) <<8  ) |
                                  ( ((int) byteInput[i +3] & 0xFF)      )  ;
            i += 4;
            wordIndex++;

            if ( wordIndex < 16 ) {     // buffer is not full yet
                continue;
            }
            //computeHash(intArray);      // buffer is full, computing hash
            wordIndex = 0;
        }

        // writing last incomplete word
        // after writing free byte positions are set to "0"s

        nBytes = toByte - i +1;
        if ( nBytes != 0 ) {

            int w =  ((int) byteInput[i] & 0xFF) <<24 ;

            if ( nBytes != 1 ) {
                w |= ((int) byteInput[i +1] & 0xFF) <<16 ;
                if ( nBytes != 2) {
                    w |= ((int) byteInput[i +2] & 0xFF) <<8 ;
                }
            }
            intArray[wordIndex] = w;
        }

        return ;
    }

    public static void main(String[] args) {
        byte[] out = new byte[20];
        SHA1PRNG_SecureRandomImpl_KitKat prng = new SHA1PRNG_SecureRandomImpl_KitKat();
        prng.engineNextBytes(out);
    }
}
