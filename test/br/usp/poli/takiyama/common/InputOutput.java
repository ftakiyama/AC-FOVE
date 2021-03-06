/*******************************************************************************
 * Copyright 2014 Felipe Takiyama
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package br.usp.poli.takiyama.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class InputOutput<I, O> {
	
	private class IONode {
		private final I in;
		private final List<O> out;
		
		private IONode(I in, O out1, O ... outs) {
			this.in = in;
			this.out = new ArrayList<O>(outs.length + 1);
			this.out.add(out1);
			this.out.addAll(Arrays.asList(outs));
		}
		
		private Object[] asList() {
			Object [] list = new Object[out.size() + 1];
			list[0] = in;
			for (int i = 0; i < out.size(); i++) {
				list[i + 1] = out.get(i);
			}
			return list;
		}
	}
	
	private final List<IONode> inOuts;
	
	private InputOutput() {
		inOuts = new ArrayList<IONode>();
	}
	
	public static <T, U> InputOutput<T, U> getInstance() {
		return new InputOutput<T, U>();
	}
	
	public void add(I input, O output) {
		IONode node = new IONode(input, output);
		inOuts.add(node);
	}
	
	public void add(I input, O output, O ... moreOutputs) {
		IONode node = new IONode(input, output, moreOutputs);
		inOuts.add(node);
	}
	
	public Collection<Object[]> toCollection() {
		List<Object[]> collection = new ArrayList<Object[]>(inOuts.size());
		
		for (int i = 0; i < inOuts.size(); i++) {
			Object [] list = inOuts.get(i).asList();
			collection.add(list);
		}
		
		return collection;
	}	
}
