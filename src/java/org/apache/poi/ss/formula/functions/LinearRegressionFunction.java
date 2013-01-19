/*
 *  ====================================================================
 *    Licensed to the Apache Software Foundation (ASF) under one or more
 *    contributor license agreements.  See the NOTICE file distributed with
 *    this work for additional information regarding copyright ownership.
 *    The ASF licenses this file to You under the Apache License, Version 2.0
 *    (the "License"); you may not use this file except in compliance with
 *    the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * ====================================================================
 */

package org.apache.poi.ss.formula.functions;

import org.apache.poi.ss.formula.TwoDEval;
import org.apache.poi.ss.formula.eval.ErrorEval;
import org.apache.poi.ss.formula.eval.EvaluationException;
import org.apache.poi.ss.formula.eval.NumberEval;
import org.apache.poi.ss.formula.eval.RefEval;
import org.apache.poi.ss.formula.eval.ValueEval;
import org.apache.poi.ss.formula.functions.LookupUtils.ValueVector;

/**
 * Base class for linear regression functions.
 *
 * Calculates the linear regression line that is used to predict y values from x values<br/>
 * (http://introcs.cs.princeton.edu/java/97data/LinearRegression.java.html)
 * <b>Syntax</b>:<br/>
 * <b>INTERCEPT</b>(<b>arrayX</b>, <b>arrayY</b>)<p/>
 * or
 * <b>SLOPE</b>(<b>arrayX</b>, <b>arrayY</b>)<p/>
 *
 *
 * @author Johan Karlsteen
 */
public final class LinearRegressionFunction extends Fixed2ArgFunction {
	
	private static abstract class ValueArray implements ValueVector {
		private final int _size;
		protected ValueArray(int size) {
			_size = size;
		}
		@Override
		public ValueEval getItem(int index) {
			if (index < 0 || index > _size) {
				throw new IllegalArgumentException("Specified index " + index
						+ " is outside range (0.." + (_size - 1) + ")");
			}
			return getItemInternal(index);
		}
		protected abstract ValueEval getItemInternal(int index);
		@Override
		public final int getSize() {
			return _size;
		}
	}

	private static final class SingleCellValueArray extends ValueArray {
		private final ValueEval _value;
		public SingleCellValueArray(ValueEval value) {
			super(1);
			_value = value;
		}
		@Override
		protected ValueEval getItemInternal(int index) {
			return _value;
		}
	}

	private static final class RefValueArray extends ValueArray {
		private final RefEval _ref;
		public RefValueArray(RefEval ref) {
			super(1);
			_ref = ref;
		}
		@Override
		protected ValueEval getItemInternal(int index) {
			return _ref.getInnerValueEval();
		}
	}

	private static final class AreaValueArray extends ValueArray {
		private final TwoDEval _ae;
		private final int _width;

		public AreaValueArray(TwoDEval ae) {
			super(ae.getWidth() * ae.getHeight());
			_ae = ae;
			_width = ae.getWidth();
		}
		@Override
		protected ValueEval getItemInternal(int index) {
			int rowIx = index / _width;
			int colIx = index % _width;
			return _ae.getValue(rowIx, colIx);
		}
	}

	public enum FUNCTION {INTERCEPT, SLOPE};
	public FUNCTION function;
	
	public LinearRegressionFunction(FUNCTION function) {
		this.function = function;
	}
	
	@Override
	public ValueEval evaluate(int srcRowIndex, int srcColumnIndex,
			ValueEval arg0, ValueEval arg1) {
		double result;
		try {
			ValueVector vvX = createValueVector(arg0);
			ValueVector vvY = createValueVector(arg1);
			int size = vvX.getSize();
			if (size == 0 || vvY.getSize() != size) {
				return ErrorEval.NA;
			}
			result = evaluateInternal(vvX, vvY, size);
		} catch (EvaluationException e) {
			return e.getErrorEval();
		}
		if (Double.isNaN(result) || Double.isInfinite(result)) {
			return ErrorEval.NUM_ERROR;
		}
		return new NumberEval(result);
	}
	
	private double evaluateInternal(ValueVector x, ValueVector y, int size)
			throws EvaluationException {

		// error handling is as if the x is fully evaluated before y
		ErrorEval firstXerr = null;
		ErrorEval firstYerr = null;
		boolean accumlatedSome = false;
		double result = 0.0;
        // first pass: read in data, compute xbar and ybar
        double sumx = 0.0, sumy = 0.0;
        
		for (int i = 0; i < size; i++) {
			ValueEval vx = x.getItem(i);
			ValueEval vy = y.getItem(i);
			if (vx instanceof ErrorEval) {
				if (firstXerr == null) {
					firstXerr = (ErrorEval) vx;
					continue;
				}
			}
			if (vy instanceof ErrorEval) {
				if (firstYerr == null) {
					firstYerr = (ErrorEval) vy;
					continue;
				}
			}
			// only count pairs if both elements are numbers
			if (vx instanceof NumberEval && vy instanceof NumberEval) {
				accumlatedSome = true;
				NumberEval nx = (NumberEval) vx;
				NumberEval ny = (NumberEval) vy;
				sumx  += nx.getNumberValue();
	            sumy  += ny.getNumberValue();
			} else {
				// all other combinations of value types are silently ignored
			}
		}
		double xbar = sumx / size;
        double ybar = sumy / size;
		
		 // second pass: compute summary statistics
        double xxbar = 0.0, xybar = 0.0;
        for (int i = 0; i < size; i++) {
			ValueEval vx = x.getItem(i);
			ValueEval vy = y.getItem(i);
			
			if (vx instanceof ErrorEval) {
				if (firstXerr == null) {
					firstXerr = (ErrorEval) vx;
					continue;
				}
			}
			if (vy instanceof ErrorEval) {
				if (firstYerr == null) {
					firstYerr = (ErrorEval) vy;
					continue;
				}
			}
			
			// only count pairs if both elements are numbers
			if (vx instanceof NumberEval && vy instanceof NumberEval) {
				NumberEval nx = (NumberEval) vx;
				NumberEval ny = (NumberEval) vy;
	            xxbar += (nx.getNumberValue() - xbar) * (nx.getNumberValue() - xbar);
	            xybar += (nx.getNumberValue() - xbar) * (ny.getNumberValue() - ybar);
			} else {
				// all other combinations of value types are silently ignored
			}
        }
        double beta1 = xybar / xxbar;
        double beta0 = ybar - beta1 * xbar;
		
		if (firstXerr != null) {
			throw new EvaluationException(firstXerr);
		}
		if (firstYerr != null) {
			throw new EvaluationException(firstYerr);
		}
		if (!accumlatedSome) {
			throw new EvaluationException(ErrorEval.DIV_ZERO);
		}
		
		if(function == FUNCTION.INTERCEPT) {
			return beta0;
		} else {
			return beta1;
		}
	}

	private static ValueVector createValueVector(ValueEval arg) throws EvaluationException {
		if (arg instanceof ErrorEval) {
			throw new EvaluationException((ErrorEval) arg);
		}
		if (arg instanceof TwoDEval) {
			return new AreaValueArray((TwoDEval) arg);
		}
		if (arg instanceof RefEval) {
			return new RefValueArray((RefEval) arg);
		}
		return new SingleCellValueArray(arg);
	}
}

