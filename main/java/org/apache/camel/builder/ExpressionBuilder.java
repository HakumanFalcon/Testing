/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.Producer;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.language.bean.BeanLanguage;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.TokenPairExpressionIterator;
import org.apache.camel.support.TokenXMLExpressionIterator;
import org.apache.camel.support.XMLTokenExpressionIterator;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.GroupIterator;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OgnlHelper;

/**
 * A helper class for working with <a href="http://camel.apache.org/expression.html">expressions</a>.
 *
 * @version 
 */
public final class ExpressionBuilder {

    /**
     * Utility classes should not have a public constructor.
     */
    private ExpressionBuilder() {
    }
    
    /**
     * Returns an expression for the inbound message attachments
     *
     * @return an expression object which will return the inbound message attachments
     */
    public static Expression attachmentsExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getAttachments();
            }

            @Override
            public String toString() {
                return "attachments";
            }
        };
    }

    /**
     * Returns an expression for the inbound message attachments
     *
     * @return an expression object which will return the inbound message attachments
     */
    public static Expression attachmentValuesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getAttachments().values();
            }

            @Override
            public String toString() {
                return "attachments";
            }
        };
    }

    /**
     * Returns an expression for the header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param headerName the name of the header the expression will return
     * @return an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object header = exchange.getIn().getHeader(headerName);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(headerName);
                }
                return header;
            }

            @Override
            public String toString() {
                return "header(" + headerName + ")";
            }
        };
    }

    /**
     * Returns an expression for the header value with the given name converted to the given type
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param headerName the name of the header the expression will return
     * @param type the type to convert to
     * @return an expression object which will return the header value
     */
    public static <T> Expression headerExpression(final String headerName, final Class<T> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object header = exchange.getIn().getHeader(headerName, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(headerName, type);
                }
                return header;
            }

            @Override
            public String toString() {
                return "headerAs(" + headerName + ", " + type + ")";
            }
        };
    }

    /**
     * Returns an expression for the header value with the given name converted to the given type
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param headerName the name of the header the expression will return
     * @param name the type to convert to as a FQN class name
     * @return an expression object which will return the header value
     */
    public static Expression headerExpression(final String headerName, final String name) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(name);
                } catch (ClassNotFoundException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }

                Object header = exchange.getIn().getHeader(headerName, type);
                if (header == null) {
                    // fall back on a property
                    header = exchange.getProperty(headerName, type);
                }
                return header;
            }

            @Override
            public String toString() {
                return "headerAs(" + headerName + ", " + name + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message header invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the header in a simple OGNL syntax
     */
    public static Expression headersOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(ognl, "headerOgnl(" + ognl + ")",
            new KeyedOgnlExpressionAdapter.KeyedEntityRetrievalStrategy() {
                public Object getKeyedEntity(Exchange exchange, String key) {
                    return exchange.getIn().getHeader(key);
                }
            });
    }

    /**
     * Returns an expression for the inbound message headers
     *
     * @return an expression object which will return the inbound headers
     */
    public static Expression headersExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeaders();
            }

            @Override
            public String toString() {
                return "headers";
            }
        };
    }

    /**
     * Returns an expression for the out header value with the given name
     * <p/>
     * Will fallback and look in properties if not found in headers.
     *
     * @param headerName the name of the header the expression will return
     * @return an expression object which will return the header value
     */
    public static Expression outHeaderExpression(final String headerName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (!exchange.hasOut()) {
                    return null;
                }

                Message out = exchange.getOut();
                Object header = out.getHeader(headerName);
                if (header == null) {
                    // let's try the exchange header
                    header = exchange.getProperty(headerName);
                }
                return header;
            }

            @Override
            public String toString() {
                return "outHeader(" + headerName + ")";
            }
        };
    }

    /**
     * Returns an expression for the outbound message headers
     *
     * @return an expression object which will return the headers, will be <tt>null</tt> if the
     * exchange is not out capable.
     */
    public static Expression outHeadersExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // only get out headers if the MEP is out capable
                if (ExchangeHelper.isOutCapable(exchange)) {
                    return exchange.getOut().getHeaders();
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "outHeaders";
            }
        };
    }

    /**
     * Returns an expression for the exchange pattern
     *
     * @see org.apache.camel.Exchange#getPattern()
     * @return an expression object which will return the exchange pattern
     */
    public static Expression exchangePatternExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getPattern();
            }

            @Override
            public String toString() {
                return "exchangePattern";
            }
        };
    }   
    
    /**
     * Returns an expression for an exception set on the exchange
     *
     * @see Exchange#getException()
     * @return an expression object which will return the exception set on the exchange
     */
    public static Expression exchangeExceptionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }
                return exception;
            }

            @Override
            public String toString() {
                return "exchangeException";
            }
        };
    }

    /**
     * Returns an expression for an exception set on the exchange
     * <p/>
     * Is used to get the caused exception that typically have been wrapped in some sort
     * of Camel wrapper exception
     * @param type the exception type
     * @see Exchange#getException(Class)
     * @return an expression object which will return the exception set on the exchange
     */
    public static Expression exchangeExceptionExpression(final Class<Exception> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException(type);
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    return ObjectHelper.getException(type, exception);
                }
                return exception;
            }

            @Override
            public String toString() {
                return "exchangeException[" + type + "]";
            }
        };
    }
    
    /**
     * Returns the expression for the exchanges exception invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression exchangeExceptionOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }
                
                if (exception == null) {
                    return null;
                }
                return new MethodCallExpression(exception, ognl).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "exchangeExceptionOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns an expression for the type converter
     *
     * @return an expression object which will return the type converter
     */
    public static Expression typeConverterExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getTypeConverter();
            }

            @Override
            public String toString() {
                return "typeConverter";
            }
        };
    }

    /**
     * Returns an expression for the {@link org.apache.camel.spi.Registry}
     *
     * @return an expression object which will return the registry
     */
    public static Expression registryExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getRegistry();
            }

            @Override
            public String toString() {
                return "registry";
            }
        };
    }

    /**
     * Returns an expression for lookup a bean in the {@link org.apache.camel.spi.Registry}
     *
     * @return an expression object which will return the bean
     */
    public static Expression refExpression(final String ref) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getRegistry().lookupByName(ref);
            }

            @Override
            public String toString() {
                return "ref(" + ref + ")";
            }
        };
    }

    /**
     * Returns an expression for the {@link org.apache.camel.CamelContext}
     *
     * @return an expression object which will return the camel context
     */
    public static Expression camelContextExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext();
            }

            @Override
            public String toString() {
                return "camelContext";
            }
        };
    }

    /**
     * Returns an expression for the {@link org.apache.camel.CamelContext} name
     *
     * @return an expression object which will return the camel context name
     */
    public static Expression camelContextNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getName();
            }

            @Override
            public String toString() {
                return "camelContextName";
            }
        };
    }

    /**
     * Returns an expression for an exception message set on the exchange
     *
     * @see <tt>Exchange.getException().getMessage()</tt>
     * @return an expression object which will return the exception message set on the exchange
     */
    public static Expression exchangeExceptionMessageExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }
                return exception != null ? exception.getMessage() : null;
            }

            @Override
            public String toString() {
                return "exchangeExceptionMessage";
            }
        };
    }

    /**
     * Returns an expression for an exception stacktrace set on the exchange
     *
     * @return an expression object which will return the exception stacktrace set on the exchange
     */
    public static Expression exchangeExceptionStackTraceExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Exception exception = exchange.getException();
                if (exception == null) {
                    exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                }
                if (exception != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    exception.printStackTrace(pw);
                    IOHelper.close(pw, sw);
                    return sw.toString();
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "exchangeExceptionStackTrace";
            }
        };
    }

    /**
     * Returns an expression for the property value of exchange with the given name
     *
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     * @deprecated use {@link #exchangePropertyExpression(String)} instead
     */
    @Deprecated
    public static Expression propertyExpression(final String propertyName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperty(propertyName);
            }

            @Override
            public String toString() {
                return "exchangeProperty(" + propertyName + ")";
            }
        };
    }
    
    /**
     * Returns an expression for the property value of exchange with the given name
     *
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     */
    public static Expression exchangePropertyExpression(final String propertyName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperty(propertyName);
            }

            @Override
            public String toString() {
                return "exchangeProperty(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for the property value of exchange with the given name invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the property in a simple OGNL syntax
     */
    public static Expression propertyOgnlExpression(final String ognl) {
        return new KeyedOgnlExpressionAdapter(ognl, "propertyOgnl(" + ognl + ")",
            new KeyedOgnlExpressionAdapter.KeyedEntityRetrievalStrategy() {
                public Object getKeyedEntity(Exchange exchange, String key) {
                    return exchange.getProperty(key);
                }
            });
    }

    /**
     * Returns an expression for the properties of exchange
     *
     * @return an expression object which will return the properties
     */
    public static Expression propertiesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getProperties();
            }

            @Override
            public String toString() {
                return "properties";
            }
        };
    }
    
    /**
     * Returns an expression for the properties of the camel context
     *
     * @return an expression object which will return the properties
     */
    public static Expression camelContextPropertiesExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getProperties();
            }

            @Override
            public String toString() {
                return "camelContextProperties";
            }
        };
    }
    
    /**
     * Returns an expression for the property value of the camel context with the given name
     *
     * @param propertyName the name of the property the expression will return
     * @return an expression object which will return the property value
     */
    public static Expression camelContextPropertyExpression(final String propertyName) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getContext().getProperty(propertyName);
            }

            @Override
            public String toString() {
                return "camelContextProperty(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for a system property value with the given name
     *
     * @param propertyName the name of the system property the expression will return
     * @return an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(final String propertyName) {
        return systemPropertyExpression(propertyName, null);
    }

    /**
     * Returns an expression for a system property value with the given name
     *
     * @param propertyName the name of the system property the expression will return
     * @param defaultValue default value to return if no system property exists
     * @return an expression object which will return the system property value
     */
    public static Expression systemPropertyExpression(final String propertyName,
                                                      final String defaultValue) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return System.getProperty(propertyName, defaultValue);
            }

            @Override
            public String toString() {
                return "systemProperty(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for a system environment value with the given name
     *
     * @param propertyName the name of the system environment the expression will return
     * @return an expression object which will return the system property value
     */
    public static Expression systemEnvironmentExpression(final String propertyName) {
        return systemEnvironmentExpression(propertyName, null);
    }

    /**
     * Returns an expression for a system environment value with the given name
     *
     * @param propertyName the name of the system environment the expression will return
     * @param defaultValue default value to return if no system environment exists
     * @return an expression object which will return the system environment value
     */
    public static Expression systemEnvironmentExpression(final String propertyName,
                                                         final String defaultValue) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = System.getenv(propertyName);
                if (answer == null) {
                    answer = defaultValue;
                }
                return answer;
            }

            @Override
            public String toString() {
                return "systemEnvironment(" + propertyName + ")";
            }
        };
    }

    /**
     * Returns an expression for the constant value
     *
     * @param value the value the expression will return
     * @return an expression object which will return the constant value
     */
    public static Expression constantExpression(final Object value) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return value;
            }

            @Override
            public String toString() {
                return "" + value;
            }
        };
    }

    /**
     * Returns an expression for evaluating the expression/predicate using the given language
     *
     * @param expression  the expression or predicate
     * @return an expression object which will evaluate the expression/predicate using the given language
     */
    public static Expression languageExpression(final String language, final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Language lan = exchange.getContext().resolveLanguage(language);
                if (lan != null) {
                    return lan.createExpression(expression).evaluate(exchange, Object.class);
                } else {
                    throw new NoSuchLanguageException(language);
                }
            }

            @Override
            public boolean matches(Exchange exchange) {
                Language lan = exchange.getContext().resolveLanguage(language);
                if (lan != null) {
                    return lan.createPredicate(expression).matches(exchange);
                } else {
                    throw new NoSuchLanguageException(language);
                }
            }

            @Override
            public String toString() {
                return "language[" + language + ":" + expression + "]";
            }
        };
    }

    /**
     * Returns an expression for a type value
     *
     * @param name the type name
     * @return an expression object which will return the type value
     */
    public static Expression typeExpression(final String name) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // it may refer to a class type
                Class<?> type = exchange.getContext().getClassResolver().resolveClass(name);
                if (type != null) {
                    return type;
                }

                int pos = name.lastIndexOf(".");
                if (pos > 0) {
                    String before = name.substring(0, pos);
                    String after = name.substring(pos + 1);
                    type = exchange.getContext().getClassResolver().resolveClass(before);
                    if (type != null) {
                        return ObjectHelper.lookupConstantFieldValue(type, after);
                    }
                }

                throw ObjectHelper.wrapCamelExecutionException(exchange, new ClassNotFoundException("Cannot find type " + name));
            }

            @Override
            public String toString() {
                return "type:" + name;
            }
        };
    }

    /**
     * Returns an expression that caches the evaluation of another expression
     * and returns the cached value, to avoid re-evaluating the expression.
     *
     * @param expression  the target expression to cache
     * @return the cached value
     */
    public static Expression cacheExpression(final Expression expression) {
        return new ExpressionAdapter() {
            private final AtomicReference<Object> cache = new AtomicReference<Object>();

            public Object evaluate(Exchange exchange) {
                Object answer = cache.get();
                if (answer == null) {
                    answer = expression.evaluate(exchange, Object.class);
                    cache.set(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return expression.toString();
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body
     */
    public static Expression bodyExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getBody();
            }

            @Override
            public String toString() {
                return "body";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression bodyOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object body = exchange.getIn().getBody();
                if (body == null) {
                    return null;
                }
                return new MethodCallExpression(body, ognl).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "bodyOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for invoking a method (support OGNL syntax) on the given expression
     *
     * @param exp   the expression to evaluate and invoke the method on its result
     * @param ognl  methods to invoke on the evaluated expression in a simple OGNL syntax
     */
    public static Expression ognlExpression(final Expression exp, final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = exp.evaluate(exchange, Object.class);
                if (value == null) {
                    return null;
                }
                return new MethodCallExpression(value, ognl).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "ognl(" + exp + ", " + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges camelContext invoking methods defined
     * in a simple OGNL notation
     *
     * @param ognl  methods to invoke on the body in a simple OGNL syntax
     */
    public static Expression camelContextOgnlExpression(final String ognl) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                CamelContext context = exchange.getContext();
                if (context == null) {
                    return null;
                }
                return new MethodCallExpression(context, ognl).evaluate(exchange);
            }

            @Override
            public String toString() {
                return "camelContextOgnl(" + ognl + ")";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     */
    public static <T> Expression bodyExpression(final Class<T> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getBody(type);
            }

            @Override
            public String toString() {
                return "bodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     */
    public static Expression bodyExpression(final String name) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(name);
                } catch (ClassNotFoundException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
                return exchange.getIn().getBody(type);
            }

            @Override
            public String toString() {
                return "bodyAs[" + name + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     */
    public static Expression mandatoryBodyExpression(final String name) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Class<?> type;
                try {
                    type = exchange.getContext().getClassResolver().resolveMandatoryClass(name);
                } catch (ClassNotFoundException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + name + "]";
            }
        };
    }

    /**
     * Returns the expression for the current thread name
     */
    public static Expression threadNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return Thread.currentThread().getName();
            }

            @Override
            public String toString() {
                return "threadName";
            }
        };
    }

    /**
     * Returns the expression for the {@code null} value
     */
    public static Expression nullExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return null;
            }

            @Override
            public String toString() {
                return "null";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type.
     * <p/>
     * Does <b>not</b> allow null bodies.
     */
    public static <T> Expression mandatoryBodyExpression(final Class<T> type) {
        return mandatoryBodyExpression(type, false);
    }

    /**
     * Returns the expression for the exchanges inbound message body converted
     * to the given type
     *
     * @param type the type
     * @param nullBodyAllowed whether null bodies is allowed and if so a null is returned,
     *                        otherwise an exception is thrown
     */
    public static <T> Expression mandatoryBodyExpression(final Class<T> type, final boolean nullBodyAllowed) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (nullBodyAllowed) {
                    if (exchange.getIn().getBody() == null) {
                        return null;
                    }

                    // if its a bean invocation then if it has no arguments then it should be threaded as null body allowed
                    if (exchange.getIn().getBody() instanceof BeanInvocation) {
                        // BeanInvocation would be stored directly as the message body
                        // do not force any type conversion attempts as it would just be unnecessary and cost a bit performance
                        // so a regular instanceof check is sufficient
                        BeanInvocation bi = (BeanInvocation) exchange.getIn().getBody();
                        if (bi.getArgs() == null || bi.getArgs().length == 0 || bi.getArgs()[0] == null) {
                            return null;
                        }
                    }
                }

                try {
                    return exchange.getIn().getMandatoryBody(type);
                } catch (InvalidPayloadException e) {
                    throw ObjectHelper.wrapCamelExecutionException(exchange, e);
                }
            }

            @Override
            public String toString() {
                return "mandatoryBodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchanges inbound message body type
     */
    public static Expression bodyTypeExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getBody().getClass();
            }

            @Override
            public String toString() {
                return "bodyType";
            }
        };
    }

    /**
     * Returns the expression for the out messages body
     */
    public static Expression outBodyExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (exchange.hasOut()) {
                    return exchange.getOut().getBody();
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "outBody";
            }
        };
    }

    /**
     * Returns the expression for the exchanges outbound message body converted
     * to the given type
     */
    public static <T> Expression outBodyExpression(final Class<T> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (exchange.hasOut()) {
                    return exchange.getOut().getBody(type);
                } else {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "outBodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the fault messages body
     */
    public static Expression faultBodyExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                return msg.isFault() ? msg.getBody() : null;
            }

            @Override
            public String toString() {
                return "faultBody";
            }
        };
    }

    /**
     * Returns the expression for the exchanges fault message body converted
     * to the given type
     */
    public static <T> Expression faultBodyExpression(final Class<T> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
                return msg.isFault() ? msg.getBody(type) : null;
            }

            @Override
            public String toString() {
                return "faultBodyAs[" + type.getName() + "]";
            }
        };
    }

    /**
     * Returns the expression for the exchange
     */
    public static Expression exchangeExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange;
            }

            @Override
            public String toString() {
                return "exchange";
            }
        };
    }

    /**
     * Returns the expression for the IN message
     */
    public static Expression inMessageExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn();
            }

            @Override
            public String toString() {
                return "inMessage";
            }
        };
    }

    /**
     * Returns the expression for the OUT message
     */
    public static Expression outMessageExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getOut();
            }

            @Override
            public String toString() {
                return "outMessage";
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type
     */
    public static Expression convertToExpression(final Expression expression, final Class<?> type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                if (type != null) {
                    return expression.evaluate(exchange, type);
                } else {
                    return expression;
                }
            }

            @Override
            public String toString() {
                return "" + expression;
            }
        };
    }

    /**
     * Returns an expression which converts the given expression to the given type the type
     * expression is evaluated to
     */
    public static Expression convertToExpression(final Expression expression, final Expression type) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object result = type.evaluate(exchange, Object.class);
                if (result != null) {
                    return expression.evaluate(exchange, result.getClass());
                } else {
                    return expression;
                }
            }

            @Override
            public String toString() {
                return "" + expression;
            }
        };
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the
     * given token
     */
    public static Expression tokenizeExpression(final Expression expression,
                                                final String token) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                Scanner scanner = ObjectHelper.getScanner(exchange, value);
                scanner.useDelimiter(token);
                return scanner;
            }

            @Override
            public String toString() {
                return "tokenize(" + expression + ", " + token + ")";
            }
        };
    }

    /**
     * Returns an {@link TokenPairExpressionIterator} expression
     */
    public static Expression tokenizePairExpression(String startToken, String endToken, boolean includeTokens) {
        return new TokenPairExpressionIterator(startToken, endToken, includeTokens);
    }

    /**
     * Returns an {@link TokenXMLExpressionIterator} expression
     */
    public static Expression tokenizeXMLExpression(String tagName, String inheritNamespaceTagName) {
        ObjectHelper.notEmpty(tagName, "tagName");

        // must be XML tokens
        if (!tagName.startsWith("<")) {
            tagName = "<" + tagName;
        }
        if (!tagName.endsWith(">")) {
            tagName = tagName + ">";
        }

        if (inheritNamespaceTagName != null) {
            if (!inheritNamespaceTagName.startsWith("<")) {
                inheritNamespaceTagName = "<" + inheritNamespaceTagName;
            }
            if (!inheritNamespaceTagName.endsWith(">")) {
                inheritNamespaceTagName = inheritNamespaceTagName + ">";
            }
        }
        return new TokenXMLExpressionIterator(tagName, inheritNamespaceTagName);
    }

    public static Expression tokenizeXMLAwareExpression(String path, char mode) {
        ObjectHelper.notEmpty(path, "path");

        return new XMLTokenExpressionIterator(path, mode);
    }
    
    public static Expression tokenizeXMLAwareExpression(String path, char mode, int group) {
        ObjectHelper.notEmpty(path, "path");

        return new XMLTokenExpressionIterator(path, mode, group);
    }

    /**
     * Returns a tokenize expression which will tokenize the string with the
     * given regex
     */
    public static Expression regexTokenizeExpression(final Expression expression,
                                                     final String regexTokenizer) {
        final Pattern pattern = Pattern.compile(regexTokenizer);
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Object value = expression.evaluate(exchange, Object.class);
                Scanner scanner = ObjectHelper.getScanner(exchange, value);
                scanner.useDelimiter(pattern);
                return scanner;
            }

            @Override
            public String toString() {
                return "regexTokenize(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    public static Expression groupIteratorExpression(final Expression expression, final String token, final int group) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it, "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                return new GroupIterator(exchange, it, token, group);
            }

            @Override
            public String toString() {
                return "group " + expression + " " + group + " times";
            }
        };
    }

    /**
     * Returns a sort expression which will sort the expression with the given comparator.
     * <p/>
     * The expression is evaluated as a {@link List} object to allow sorting.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Expression sortExpression(final Expression expression, final Comparator comparator) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                List<?> list = expression.evaluate(exchange, List.class);
                Collections.sort(list, comparator);
                return list;
            }

            @Override
            public String toString() {
                return "sort(" + expression + " by: " + comparator + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex
     * replaceAll to transform the String and return the result
     */
    public static Expression regexReplaceAll(final Expression expression,
                                             final String regex, final String replacement) {
        final Pattern pattern = Pattern.compile(regex);
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = expression.evaluate(exchange, String.class);
                if (text == null) {
                    return null;
                }
                return pattern.matcher(text).replaceAll(replacement);
            }

            @Override
            public String toString() {
                return "regexReplaceAll(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    /**
     * Transforms the expression into a String then performs the regex
     * replaceAll to transform the String and return the result
     */
    public static Expression regexReplaceAll(final Expression expression,
                                             final String regex, final Expression replacementExpression) {

        final Pattern pattern = Pattern.compile(regex);
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String text = expression.evaluate(exchange, String.class);
                String replacement = replacementExpression.evaluate(exchange, String.class);
                if (text == null || replacement == null) {
                    return null;
                }
                return pattern.matcher(text).replaceAll(replacement);
            }

            @Override
            public String toString() {
                return "regexReplaceAll(" + expression + ", " + pattern.pattern() + ")";
            }
        };
    }

    /**
     * Appends the String evaluations of the two expressions together
     */
    public static Expression append(final Expression left, final Expression right) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return left.evaluate(exchange, String.class) + right.evaluate(exchange, String.class);
            }

            @Override
            public String toString() {
                return "append(" + left + ", " + right + ")";
            }
        };
    }

    /**
     * Prepends the String evaluations of the two expressions together
     */
    public static Expression prepend(final Expression left, final Expression right) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return right.evaluate(exchange, String.class) + left.evaluate(exchange, String.class);
            }

            @Override
            public String toString() {
                return "prepend(" + left + ", " + right + ")";
            }
        };
    }

    /**
     * Returns an expression which returns the string concatenation value of the various
     * expressions
     *
     * @param expressions the expression to be concatenated dynamically
     * @return an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions) {
        return concatExpression(expressions, null);
    }

    /**
     * Returns an expression which returns the string concatenation value of the various
     * expressions
     *
     * @param expressions the expression to be concatenated dynamically
     * @param expression the text description of the expression
     * @return an expression which when evaluated will return the concatenated values
     */
    public static Expression concatExpression(final Collection<Expression> expressions, final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                StringBuilder buffer = new StringBuilder();
                for (Expression expression : expressions) {
                    String text = expression.evaluate(exchange, String.class);
                    if (text != null) {
                        buffer.append(text);
                    }
                }
                return buffer.toString();
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return expression;
                } else {
                    return "concat" + expressions;
                }
            }
        };
    }

    /**
     * Returns an Expression for the inbound message id
     */
    public static Expression messageIdExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getMessageId();
            }

            @Override
            public String toString() {
                return "messageId";
            }
        };
    }

    /**
     * Returns an Expression for the exchange id
     */
    public static Expression exchangeIdExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getExchangeId();
            }

            @Override
            public String toString() {
                return "exchangeId";
            }
        };
    }

    /**
     * Returns an Expression for the route id
     */
    public static Expression routeIdExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = null;
                UnitOfWork uow = exchange.getUnitOfWork();
                RouteContext rc = uow != null ? uow.getRouteContext() : null;
                if (rc != null) {
                    answer = rc.getRoute().getId();
                }
                if (answer == null) {
                    // fallback and get from route id on the exchange
                    answer = exchange.getFromRouteId();
                }
                return answer;
            }

            @Override
            public String toString() {
                return "routeId";
            }
        };
    }

    public static Expression dateExpression(final String command, final String pattern) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Date date;
                if ("now".equals(command)) {
                    date = new Date();
                } else if (command.startsWith("header.") || command.startsWith("in.header.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    date = exchange.getIn().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot find java.util.Date object at command: " + command);
                    }
                } else if (command.startsWith("out.header.")) {
                    String key = command.substring(command.lastIndexOf('.') + 1);
                    date = exchange.getOut().getHeader(key, Date.class);
                    if (date == null) {
                        throw new IllegalArgumentException("Cannot find java.util.Date object at command: " + command);
                    }
                } else if ("file".equals(command)) {
                    Long num = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
                    if (num != null && num > 0) {
                        date = new Date(num.longValue());
                    } else {
                        date = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Date.class);
                        if (date == null) {
                            throw new IllegalArgumentException("Cannot find " + Exchange.FILE_LAST_MODIFIED + " header at command: " + command);
                        }
                    }
                } else {
                    throw new IllegalArgumentException("Command not supported for dateExpression: " + command);
                }

                SimpleDateFormat df = new SimpleDateFormat(pattern);
                return df.format(date);
            }

            @Override
            public String toString() {
                return "date(" + command + ":" + pattern + ")";
            }
        };
    }

    public static Expression simpleExpression(final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // resolve language using context to have a clear separation of packages
                // must call evaluate to return the nested language evaluate when evaluating
                // stacked expressions
                Language language = exchange.getContext().resolveLanguage("simple");
                return language.createExpression(expression).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "simple(" + expression + ")";
            }
        };
    }
   
    public static Expression beanExpression(final String expression) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                // resolve language using context to have a clear separation of packages
                // must call evaluate to return the nested language evaluate when evaluating
                // stacked expressions
                Language language = exchange.getContext().resolveLanguage("bean");
                return language.createExpression(expression).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "bean(" + expression + ")";
            }
        };
    }
    
    public static Expression beanExpression(final Class<?> beanType, final String methodName) {
        return BeanLanguage.bean(beanType, methodName);        
    }

    public static Expression beanExpression(final Object bean, final String methodName) {
        return BeanLanguage.bean(bean, methodName);        
    }

    public static Expression beanExpression(final String beanRef, final String methodName) {
        String expression = methodName != null ? beanRef + "." + methodName : beanRef;
        return beanExpression(expression);
    }

    /**
     * Returns an expression processing the exchange to the given endpoint uri
     *
     * @param uri endpoint uri to send the exchange to
     * @return an expression object which will return the OUT body
     */
    public static Expression toExpression(final String uri) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                Endpoint endpoint = exchange.getContext().getEndpoint(uri);
                if (endpoint == null) {
                    throw new NoSuchEndpointException(uri);
                }

                Producer producer;
                try {
                    producer = endpoint.createProducer();
                    producer.start();
                    producer.process(exchange);
                    producer.stop();
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }

                // return the OUT body, but check for exchange pattern
                if (ExchangeHelper.isOutCapable(exchange)) {
                    return exchange.getOut().getBody();
                } else {
                    return exchange.getIn().getBody();
                }
            }

            @Override
            public String toString() {
                return "to(" + uri + ")";
            }
        };
    }

    public static Expression fileNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
            }

            @Override
            public String toString() {
                return "file:name";
            }
        };
    }

    public static Expression fileOnlyNameExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String answer = exchange.getIn().getHeader(Exchange.FILE_NAME_ONLY, String.class);
                if (answer == null) {
                    answer = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                    answer = FileUtil.stripPath(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return "file:onlyname";
            }
        };
    }

    public static Expression fileNameNoExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.stripExt(name);
            }

            @Override
            public String toString() {
                return "file:name.noext";
            }
        };
    }

    public static Expression fileNameNoExtensionSingleExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.stripExt(name, true);
            }

            @Override
            public String toString() {
                return "file:name.noext.single";
            }
        };
    }

    public static Expression fileOnlyNameNoExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = fileOnlyNameExpression().evaluate(exchange, String.class);
                return FileUtil.stripExt(name);
            }

            @Override
            public String toString() {
                return "file:onlyname.noext";
            }
        };
    }

    public static Expression fileOnlyNameNoExtensionSingleExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = fileOnlyNameExpression().evaluate(exchange, String.class);
                return FileUtil.stripExt(name, true);
            }

            @Override
            public String toString() {
                return "file:onlyname.noext.single";
            }
        };
    }

    public static Expression fileExtensionExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.onlyExt(name);
            }

            @Override
            public String toString() {
                return "file:ext";
            }
        };
    }

    public static Expression fileExtensionSingleExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                String name = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
                return FileUtil.onlyExt(name, true);
            }

            @Override
            public String toString() {
                return "file:ext.single";
            }
        };
    }

    public static Expression fileParentExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileParent", String.class);
            }

            @Override
            public String toString() {
                return "file:parent";
            }
        };
    }

    public static Expression filePathExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFilePath", String.class);
            }

            @Override
            public String toString() {
                return "file:path";
            }
        };
    }

    public static Expression fileAbsolutePathExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
            }

            @Override
            public String toString() {
                return "file:absolute.path";
            }
        };
    }

    public static Expression fileAbsoluteExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader("CamelFileAbsolute", Boolean.class);
            }

            @Override
            public String toString() {
                return "file:absolute";
            }
        };
    }

    public static Expression fileSizeExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_LENGTH, Long.class);
            }

            @Override
            public String toString() {
                return "file:length";
            }
        };
    }

    public static Expression fileLastModifiedExpression() {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
            }

            @Override
            public String toString() {
                return "file:modified";
            }
        };
    }

    public static Expression propertiesComponentExpression(final String key, final String locations) {
        return new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                try {
                    if (locations != null) {
                        // the properties component is optional as we got locations
                        // getComponent will create a new component if none already exists
                        Component component = exchange.getContext().getComponent("properties");
                        PropertiesComponent pc = exchange.getContext().getTypeConverter()
                                .mandatoryConvertTo(PropertiesComponent.class, component);
                        // enclose key with {{ }} to force parsing
                        String[] paths = locations.split(",");
                        return pc.parseUri(pc.getPrefixToken() + key + pc.getSuffixToken(), paths);
                    } else {
                        // the properties component is mandatory if no locations provided
                        Component component = exchange.getContext().hasComponent("properties");
                        if (component == null) {
                            throw new IllegalArgumentException("PropertiesComponent with name properties must be defined"
                                    + " in CamelContext to support property placeholders in expressions");
                        }
                        PropertiesComponent pc = exchange.getContext().getTypeConverter()
                                .mandatoryConvertTo(PropertiesComponent.class, component);
                        // enclose key with {{ }} to force parsing
                        return pc.parseUri(pc.getPrefixToken() + key + pc.getSuffixToken());
                    }
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }

            @Override
            public String toString() {
                return "properties(" + key + ")";
            }
        };
    }

    /**
     * Expression adapter for OGNL expression from Message Header or Exchange property
     */
    private static class KeyedOgnlExpressionAdapter extends ExpressionAdapter {
        private final String ognl;
        private final String toStringValue;
        private final KeyedEntityRetrievalStrategy keyedEntityRetrievalStrategy;

        public KeyedOgnlExpressionAdapter(String ognl, String toStringValue, 
                                          KeyedEntityRetrievalStrategy keyedEntityRetrievalStrategy) {
            this.ognl = ognl;
            this.toStringValue = toStringValue;
            this.keyedEntityRetrievalStrategy = keyedEntityRetrievalStrategy;
        }

        public Object evaluate(Exchange exchange) {
            // try with full name first
            Object property = keyedEntityRetrievalStrategy.getKeyedEntity(exchange, ognl);
            if (property != null) {
                return property;
            }

            // Split ognl except when this is not a Map, Array
            // and we would like to keep the dots within the key name
            List<String> methods = OgnlHelper.splitOgnl(ognl);

            // remove any OGNL operators so we got the pure key name
            String key = OgnlHelper.removeOperators(methods.get(0));

            property = keyedEntityRetrievalStrategy.getKeyedEntity(exchange, key);
            if (property == null) {
                return null;
            }
            // the remainder is the rest of the ognl without the key
            String remainder = ObjectHelper.after(ognl, key);
            return new MethodCallExpression(property, remainder).evaluate(exchange);
        }

        @Override
        public String toString() {
            return toStringValue;
        }

        /**
         * Strategy to retrieve the value based on the key
         */
        public interface KeyedEntityRetrievalStrategy {
            Object getKeyedEntity(Exchange exchange, String key);
        }
    };

}
