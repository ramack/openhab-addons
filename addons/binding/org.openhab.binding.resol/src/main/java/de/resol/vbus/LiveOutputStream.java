/**
 * Copyright (C) 2008-2016, RESOL - Elektronische Regelungen GmbH.
 * Copyright (C) 2016, Daniel Wippermann.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package de.resol.vbus;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The `LiveOutputStream` class wraps another `OutputStream` instance and
 * uses it to send live representations of VBus `Header` instances over it.
 */
public class LiveOutputStream {

	protected OutputStream os;
	
	/**
	 * Create a `LiveOutputStream` instance, initializing its members to the given values.
	 * @param os OutputStream to send raw VBus data to.
	 */
	public LiveOutputStream(OutputStream os) {
		this.os = os;
	}
	
	/**
	 * Converts the given `Header` instance into its live representation and
	 * sends it to the wrapped `OutputStream`.
	 * 
	 * @param header `Header` instance to send.
	 * @throws IOException
	 */
	public void writeHeader(Header header) throws IOException {
		os.write(header.toLiveBuffer(null, 0, 0));
	}

}
