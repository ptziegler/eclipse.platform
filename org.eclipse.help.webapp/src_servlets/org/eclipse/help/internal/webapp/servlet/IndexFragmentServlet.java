/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.help.internal.webapp.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.help.IIndex;
import org.eclipse.help.IIndexEntry;
import org.eclipse.help.ITopic;
import org.eclipse.help.internal.HelpPlugin;
import org.eclipse.help.internal.base.BaseHelpSystem;
import org.eclipse.help.internal.webapp.WebappResources;
import org.eclipse.help.internal.webapp.data.EnabledTopicUtils;
import org.eclipse.help.internal.webapp.data.UrlUtil;

/*
 * Creates xml representing selected parts of the index
 * Parameter "start" represents the part of the index to start reading from
 * Parameter "size" indicates the number of entries to read, no size parameter
 * or a negatove size parameter indicates that all entries which match the start 
 * letters should be displayed.
 * Parameter "offset" represents the starting point relative to the start
 */
public class IndexFragmentServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;
	private static Map locale2Response = new WeakHashMap();
	private String startParameter;
	private String sizeParameter;
	private String entryParameter;
	private String modeParameter;
	private int size;
	private int entry;
	private static final String NEXT = "next"; //$NON-NLS-1$
	private static final String PREVIOUS = "previous"; //$NON-NLS-1$
	private static final String SIZE = "size"; //$NON-NLS-1$
	private static final String MODE = "mode"; //$NON-NLS-1$
	private static final String ENTRY = "entry"; //$NON-NLS-1$

	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String locale = UrlUtil.getLocale(req, resp);
		startParameter = req.getParameter("start"); //$NON-NLS-1$
		if (startParameter != null) {
			startParameter = startParameter.toLowerCase();
		}

		size = 30;
		sizeParameter = req.getParameter(SIZE); 
		if (sizeParameter != null) {
			try {
			    size = Integer.parseInt(sizeParameter);
			} catch (NumberFormatException n) {
			}
		}
		
		entry = -1;
		entryParameter = req.getParameter(ENTRY); 
		if (entryParameter != null) {
			try {
			    entry = Integer.parseInt(entryParameter);
			} catch (NumberFormatException n) {
			}
		}
		
		modeParameter = req.getParameter(MODE);
		
		req.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
		resp.setContentType("application/xml; charset=UTF-8"); //$NON-NLS-1$
		// Cache suppression required if not in infocenter mode because the set of disabled 
		// topics could change between requests
		if (BaseHelpSystem.getMode() != BaseHelpSystem.MODE_INFOCENTER) {
		    resp.setHeader("Cache-Control","no-cache");   //$NON-NLS-1$//$NON-NLS-2$
		    resp.setHeader("Pragma","no-cache");  //$NON-NLS-1$ //$NON-NLS-2$
		    resp.setDateHeader ("Expires", 0); 	 //$NON-NLS-1$	
		}
		Serializer serializer = new Serializer(locale);
		String response = serializer.generateIndexXml();	
		locale2Response.put(locale, response);
		resp.getWriter().write(response);
	}
	
	/*
	 * Class which creates the xml file based upon the request parameters
	 */
	private class Serializer {
		
		private IIndex index;
		private StringBuffer buf;
		private int count = 0;
		private String locale;
		private List entryList;
		private IIndexEntry[] entries;
		private boolean enablePrevious = true;
		private boolean enableNext = true;

		public Serializer(String locale) {
			this.locale = locale;
			index = HelpPlugin.getIndexManager().getIndex(locale);
			buf = new StringBuffer();
		}
		
		/*
		 * There are three modes of generation, current page, next page and previous page.
		 * Current page returns a screenful of entries starting at the startParameter.
		 * Next page returns a screenful of entries starting after but not including the start parameter.
		 * Previous page returns a screenful of entries going back from the start parameter
		 */
		private String generateIndexXml() {
			
			entries = index.getEntries();
			if (entries.length == 0) {
				generateEmptyIndexMessage();
			} else {
				entryList = new ArrayList();
				int nextEntry = findFirstEntry(entries);
				if (PREVIOUS.equals(modeParameter)) {
					int remaining = getPreviousEntries(nextEntry, size);
					getNextEntries(nextEntry, remaining);
				} else {
					int remaining = getNextEntries(nextEntry, size);
					if (remaining == size) {
						// Generate just the last entry
						size = 1;
						getPreviousEntries(nextEntry, 1);
					}
				}
				for (Iterator iter = entryList.iterator(); iter.hasNext();) {
					Integer entryId = (Integer)iter.next();
					generateEntry(entries[entryId.intValue()], 0, "e" + entryId.intValue()); //$NON-NLS-1$
				}
			}
			String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tree_data enableNext = \"" //$NON-NLS-1$
				+ Boolean.toString(enableNext) + "\" enablePrevious = \"" + Boolean.toString(enablePrevious) + "\">\n"; //$NON-NLS-1$ //$NON-NLS-2$
			buf.append("</tree_data>\n"); //$NON-NLS-1$
			return header + buf.toString();
		}
		
		private int getCategory(String keyword) {
			if (keyword != null && keyword.length() > 0) {
				char c = keyword.charAt(0);
				if (Character.isDigit(c)) {
					return 2;
				} else if (Character.isLetter(c)) {
					return 3;
				}
				return 1;
			}
			return 4;
		}
		
		private int compare (String left, String right) {
			int catLeft = getCategory(left);
			int catRight = getCategory(right);
			if (catLeft != catRight) {
				return catLeft - catRight;
			} else {
				return left.compareTo(right);
			}
		}

		private int findFirstEntry(IIndexEntry[] entries) {
			if (NEXT.equals(modeParameter)) {
				if (entry >= entries.length - 1) {
					return entries.length - 1;
				} else {
					return entry + 1;
				}
			}
			if (PREVIOUS.equals(modeParameter)) {
				if (entry <= 0) {
					return 0;
				} else {
					return entry - 1;
				}
			}
			if (startParameter == null) {
				return 0;
			}
			int nextEntry = 0;
			while (nextEntry < entries.length) {
			    String keyword = entries[nextEntry].getKeyword().toLowerCase();	            
				if (keyword != null) {					
					if (compare(startParameter, keyword) <= 0) { 
				        break;
					}
				}
				nextEntry++;
			}
			return nextEntry;
		}
		
		private int getNextEntries(int nextEntry, int remaining) {
			while (nextEntry < entries.length) {
				int entrySize = enabledEntryCount(entries[nextEntry]);
				if (remaining == size || remaining > entrySize) {
                    entryList.add(new Integer(nextEntry));
                    setFlags(nextEntry);
                    remaining -= entrySize;
				} else {
					break;
				}
				nextEntry++;
			}	
			return remaining;
		}

		private int getPreviousEntries(int nextEntry, int remaining) {
			nextEntry--;
			while (nextEntry >= 0) {
				int entrySize = enabledEntryCount(entries[nextEntry]);
				if (remaining == size || remaining > entrySize) {
                    entryList.add(0, new Integer(nextEntry));

                    setFlags(nextEntry);
                    remaining -= entrySize;
				} else {
					break;
				}
				nextEntry--;
			}
			return remaining;
		}		

		private void setFlags(int nextEntry) {
			if (nextEntry == 0) {
				enablePrevious = false;
			}
			if (nextEntry == entries.length - 1) {
				enableNext = false;
			}			
		}
		
		private int enabledEntryCount(IIndexEntry entry) {
			if (!EnabledTopicUtils.isEnabled(entry)) return 0;
			if (entry.getKeyword() == null || entry.getKeyword().length() == 0) {
				return 0;
			}
			int count = 1;
		    ITopic[] topics = EnabledTopicUtils.getEnabled(entry.getTopics());
			IIndexEntry[] subentries = EnabledTopicUtils.getEnabled(entry.getSubentries());
			if (topics.length > 1) {
				count += topics.length;
			}
			for (int i=0;i<subentries.length;++i) {
				count += enabledEntryCount(subentries[i]);
			}
            return count;
		}
		
		private void generateEmptyIndexMessage() {
			buf.append("<node"); //$NON-NLS-1$			
			buf.append('\n' + "      title=\"" + XMLGenerator.xmlEscape(WebappResources.getString("IndexEmpty", UrlUtil.getLocale(locale))) + '"'); //$NON-NLS-1$ //$NON-NLS-2$
			buf.append('\n' + "      id=\"no_index\""); //$NON-NLS-1$
			buf.append(">\n"); //$NON-NLS-1$		
			buf.append("</node>\n"); //$NON-NLS-1$	
			enableNext = false;
			enablePrevious = false;
		}
		
		private void generateEntry(IIndexEntry entry, int level, String id) {
			if (!EnabledTopicUtils.isEnabled(entry)) return;
			if (entry.getKeyword() != null && entry.getKeyword().length() > 0) {
				ITopic[] topics = EnabledTopicUtils.getEnabled(entry.getTopics());
				IIndexEntry[] subentries = EnabledTopicUtils.getEnabled(entry.getSubentries());
				boolean multipleTopics = topics.length > 1;
				boolean singleTopic = topics.length == 1;
				
				buf.append("<node"); //$NON-NLS-1$
				if (entry.getKeyword() != null) { 
					buf.append('\n' + "      title=\"" + XMLGenerator.xmlEscape(entry.getKeyword()) + '"'); //$NON-NLS-1$
				}
				
				buf.append('\n' + "      id=\"" + id + '"'); //$NON-NLS-1$			
			
				String href;
				if (singleTopic) {
					href = UrlUtil.getHelpURL((entry.getTopics()[0]).getHref());
				    buf.append('\n' + "      href=\"" +  //$NON-NLS-1$
				    	XMLGenerator.xmlEscape(href) + "\""); //$NON-NLS-1$
				}
				buf.append(">\n"); //$NON-NLS-1$
				
				if (multipleTopics || subentries.length > 0) {
					if (multipleTopics) generateTopicList(entry);
					generateSubentries(entry, level + 1);
				}
				
				buf.append("</node>\n"); //$NON-NLS-1$	
			}
		}
		
		private void generateSubentries(IIndexEntry entry, int level) {
			IIndexEntry[] subentries = entry.getSubentries();
			for (int i=0;i<subentries.length;++i) {
				generateEntry(subentries[i], level, "s" + count++); //$NON-NLS-1$
			}
		}
		
		private void generateTopicList(IIndexEntry entry) {
			ITopic[] topics = entry.getTopics();
			
			for (int i = 0; i < topics.length; ++i) {
				ITopic topic = (ITopic)topics[i]; 
				
				//
				String label = UrlUtil.htmlEncode(topic.getLabel());
                if (label == null) {
                	label = UrlUtil.htmlEncode(topic.getLabel());
                }
                
			
				buf.append("<node"); //$NON-NLS-1$
				if (entry.getKeyword() != null) { 
					buf.append('\n' + "      title=\"" + label + '"'); //$NON-NLS-1$ 
				}
				
				count++;
				buf.append('\n' + "      id=\"i" + count + '"'); //$NON-NLS-1$							
				String href = UrlUtil.getHelpURL(topic.getHref());	
				buf.append('\n' + "      href=\""  //$NON-NLS-1$
					+ XMLGenerator.xmlEscape(href) + "\""); //$NON-NLS-1$
				buf.append(">\n"); //$NON-NLS-1$
				buf.append("</node>\n"); //$NON-NLS-1$	

			}
		}	
	}

}
