package raptor.swt.chat;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ScrollBar;

import raptor.Quadrant;
import raptor.Raptor;
import raptor.chat.ChatEvent;
import raptor.chat.ChatType;
import raptor.connector.Connector;
import raptor.connector.ConnectorListener;
import raptor.connector.ics.IcsUtils;
import raptor.pref.PreferenceKeys;
import raptor.pref.RaptorPreferenceStore;
import raptor.service.SoundService;
import raptor.service.ChatService.ChatListener;
import raptor.swt.ItemChangedListener;
import raptor.swt.chat.controller.ChannelController;
import raptor.swt.chat.controller.PersonController;
import raptor.util.LaunchBrowser;

public abstract class ChatConsoleController implements PreferenceKeys {
	public static final double CLEAN_PERCENTAGE = .33;
	public static final int TEXT_CHUNK_SIZE = 1000;
	private static final Log LOG = LogFactory
			.getLog(ChatConsoleController.class);

	protected Connector connector;
	protected ChatConsole chatConsole;
	protected boolean isBeingReparented;
	protected ChatListener chatServiceListener = new ChatListener() {
		public void chatEventOccured(final ChatEvent event) {
			if (chatConsole != null && !chatConsole.isDisposed()) {
				if (!isBeingReparented) {
					chatConsole.getDisplay().asyncExec(new Runnable() {
						public void run() {
							try {
								onChatEvent(event);
							} catch (Throwable t) {
								connector.onError("onChatEvent", t);
							}
						}
					});
				} else {
					eventsWhileBeingReparented.add(event);
				}
			}
		}
	};

	protected boolean hasUnseenText;
	protected boolean ignoreAwayList;
	protected boolean isDirty;
	protected String sourceOfLastTellReceived;
	protected List<ChatEvent> awayList = new ArrayList<ChatEvent>(100);
	protected List<ChatEvent> eventsWhileBeingReparented = Collections
			.synchronizedList(new ArrayList<ChatEvent>(100));
	protected List<ItemChangedListener> itemChangedListeners = new ArrayList<ItemChangedListener>(
			5);

	protected KeyListener inputTextKeyListener = new KeyAdapter() {
		@Override
		public void keyReleased(KeyEvent arg0) {
			if (isIgnoringActions()) {
				return;
			}
			if (arg0.character == '\r') {
				onSendOutputText();
				chatConsole.outputText.forceFocus();
				chatConsole.outputText.setSelection(chatConsole.outputText
						.getCharCount());
			} else if (IcsUtils.LEGAL_CHARACTERS.indexOf(arg0.character) != -1
					&& arg0.stateMask == 0) {
				onAppendOutputText("" + arg0.character);
			} else {
				chatConsole.outputText.forceFocus();
				chatConsole.outputText.setSelection(chatConsole.outputText
						.getCharCount());
			}

		}
	};

	protected KeyListener outputKeyListener = new KeyAdapter() {
		@Override
		public void keyReleased(KeyEvent arg0) {
			if (isIgnoringActions()) {
				return;
			}
			if (arg0.character == '\r') {
				onSendOutputText();
			}
		}
	};

	protected KeyListener outputHistoryListener = new KeyAdapter() {
		protected List<String> sentText = new ArrayList<String>(50);
		protected int sentTextIndex = 0;

		@Override
		public void keyReleased(KeyEvent arg0) {
			if (isIgnoringActions()) {
				return;
			}
			if (arg0.keyCode == SWT.ARROW_UP) {
				System.err.println("In outputHistoryListener arrow up");

				if (sentTextIndex >= 0) {
					if (sentTextIndex > 0) {
						sentTextIndex--;
					}
					if (!sentText.isEmpty()) {
						chatConsole.outputText.setText(sentText
								.get(sentTextIndex));
						chatConsole.outputText
								.setSelection(chatConsole.inputText
										.getCharCount() + 1);
					}
				}
			} else if (arg0.keyCode == SWT.ARROW_DOWN) {
				System.err.println("In outputHistoryListener arrow down");

				if (sentTextIndex < sentText.size() - 1) {
					sentTextIndex++;
					chatConsole.outputText.setText(sentText.get(sentTextIndex));
					chatConsole.outputText.setSelection(chatConsole.inputText
							.getCharCount() + 1);
				} else {
					chatConsole.outputText.setText("");
				}
			} else if (arg0.character == '\r') {
				System.err.println("In outputHistoryListener CR");

				if (sentText.size() > 50) {
					sentText.remove(0);
				}
				sentText.add(chatConsole.outputText.getText().substring(0,
						chatConsole.outputText.getText().length()));
				sentTextIndex = sentText.size() - 1;
			}
		}

	};

	protected KeyListener functionKeyListener = new KeyAdapter() {
		@Override
		public void keyReleased(KeyEvent arg0) {
			if (isIgnoringActions()) {
				return;
			}
			if (arg0.keyCode == SWT.F3) {
				connector.onAcceptKeyPress();
			} else if (arg0.keyCode == SWT.F4) {
				connector.onDeclineKeyPress();
			} else if (arg0.keyCode == SWT.F6) {
				connector.onAbortKeyPress();
			} else if (arg0.keyCode == SWT.F7) {
				connector.onRematchKeyPress();
			} else if (arg0.keyCode == SWT.F9) {
				if (sourceOfLastTellReceived != null) {
					chatConsole.outputText.setText(connector
							.getTellToString(sourceOfLastTellReceived));
					chatConsole.outputText.setSelection(chatConsole.outputText
							.getCharCount() + 1);
				}
			}
		}

	};

	protected MouseListener inputTextClickListener = new MouseAdapter() {

		@Override
		public void mouseUp(MouseEvent e) {
			if (isIgnoringActions()) {
				return;
			}
			if (e.button == 1) {
				int caretPosition = chatConsole.inputText.getCaretOffset();

				String url = ChatUtils.getUrl(chatConsole.inputText,
						caretPosition);
				if (url != null) {
					LaunchBrowser.openURL(url);
					return;
				}

				String quotedText = ChatUtils.getQuotedText(
						chatConsole.inputText, caretPosition);
				if (quotedText != null) {
					connector.sendMessage(quotedText);
					return;
				}

			}
			if (e.button == 3) {
				int caretPosition = 0;
				try {
					caretPosition = chatConsole.inputText
							.getOffsetAtLocation(new Point(e.x, e.y));
				} catch (IllegalArgumentException iae) {
					return;
				}

				String word = ChatUtils.getWord(chatConsole.inputText,
						caretPosition);

				Menu menu = new Menu(chatConsole.getShell(), SWT.POP_UP);

				// TO DO: move this down into the connector.
				if (connector.isLikelyPerson(word)) {
					final String person = connector.parsePerson(word);
					MenuItem item = new MenuItem(menu, SWT.PUSH);
					item.setText("Add a tab for person: " + person);
					item.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event e) {
							ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(
									new PersonController(connector, person));
							Raptor.getInstance().getRaptorWindow()
									.addRaptorWindowItem(windowItem, false);
							ChatUtils
									.appendPreviousChatsToController(windowItem.console);
						}
					});

					final String[][] connectorPersonItems = connector
							.getPersonActions(person);
					if (connectorPersonItems != null) {
						for (int i = 0; i < connectorPersonItems.length; i++) {
							item = new MenuItem(menu, SWT.PUSH);
							item.setText(connectorPersonItems[i][0]);
							final int index = i;
							item.addListener(SWT.Selection, new Listener() {
								public void handleEvent(Event e) {
									connector
											.sendMessage(connectorPersonItems[index][1]);
								}
							});
						}
					}
				}
				if (connector.isLikelyChannel(word)) {
					MenuItem item = new MenuItem(menu, SWT.SEPARATOR);
					final String channel = connector.parseChannel(word);

					item = new MenuItem(menu, SWT.PUSH);
					item.setText("Add a tab for channel: " + channel);
					item.addListener(SWT.Selection, new Listener() {
						public void handleEvent(Event e) {
							ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(
									new ChannelController(connector, channel));
							Raptor.getInstance().getRaptorWindow()
									.addRaptorWindowItem(windowItem, false);
							ChatUtils
									.appendPreviousChatsToController(windowItem.console);
						}
					});

					final String[][] connectorChannelItems = connector
							.getChannelActions(channel);
					if (connectorChannelItems != null) {
						for (int i = 0; i < connectorChannelItems.length; i++) {
							item = new MenuItem(menu, SWT.PUSH);
							item.setText(connectorChannelItems[i][0]);
							final int index = i;
							item.addListener(SWT.Selection, new Listener() {
								public void handleEvent(Event e) {
									connector
											.sendMessage(connectorChannelItems[index][1]);
								}
							});
						}
					}
				}
				if (connector.isLikelyGameId(word)) {
					MenuItem item = new MenuItem(menu, SWT.SEPARATOR);
					String gameId = connector.parseGameId(word);

					final String[][] gameIdItems = connector
							.getGameIdActions(gameId);
					if (gameIdItems != null) {
						for (int i = 0; i < gameIdItems.length; i++) {
							item = new MenuItem(menu, SWT.PUSH);
							item.setText(gameIdItems[i][0]);
							final int index = i;
							item.addListener(SWT.Selection, new Listener() {
								public void handleEvent(Event e) {
									connector
											.sendMessage(gameIdItems[index][1]);
								}
							});
						}
					}
				}

				if (menu.getItemCount() > 0) {
					LOG.debug("Showing popup with " + menu.getItemCount()
							+ " items. "
							+ chatConsole.inputText.toDisplay(e.x, e.y));
					menu.setLocation(chatConsole.inputText.toDisplay(e.x, e.y));
					menu.setVisible(true);
					while (!menu.isDisposed() && menu.isVisible()) {
						if (!chatConsole.getDisplay().readAndDispatch())
							chatConsole.getDisplay().sleep();
					}
				}
				menu.dispose();
			}
		}

	};

	protected ConnectorListener connectorListener = new ConnectorListener() {
		public void onConnect() {
			fireItemChanged();
		}

		public void onConnecting() {
			fireItemChanged();
		}

		public void onDisconnect() {
			fireItemChanged();
		}
	};

	protected boolean isSoundDisabled = false;

	public ChatConsoleController(Connector connector) {
		this.connector = connector;
		connector.addConnectorListener(connectorListener);
	}

	protected void addInputTextKeyListeners() {
		if (!isIgnoringActions()) {
			chatConsole.outputText.addKeyListener(functionKeyListener);
			chatConsole.outputText.addKeyListener(outputHistoryListener);
			chatConsole.outputText.addKeyListener(outputKeyListener);

			chatConsole.inputText.addKeyListener(inputTextKeyListener);
			chatConsole.inputText.addKeyListener(functionKeyListener);
			chatConsole.inputText.addKeyListener(outputHistoryListener);
		}
	}

	public void addItemChangedListener(ItemChangedListener listener) {
		itemChangedListeners.add(listener);
	}

	protected void addMouseListeners() {
		if (!isIgnoringActions()) {
			chatConsole.inputText.addMouseListener(inputTextClickListener);
		}
	}

	protected void adjustAwayButtonEnabled() {
		if (!isIgnoringActions()) {
			chatConsole.setButtonEnabled(!awayList.isEmpty(),
					ChatConsole.AWAY_BUTTON);
		}
	}

	public boolean confirmClose() {
		return true;
	}

	protected void decorateForegroundColor(ChatEvent event, String message,
			int textStartPosition) {
		Color color = getPreferences().getColor(event);
		if (color == null) {
			color = chatConsole.inputText.getForeground();
		}

		String prompt = connector.getPrompt();
		if (message.endsWith(prompt)) {
			message = message.substring(0, message.length() - prompt.length());
		}

		chatConsole.inputText
				.setStyleRange(new StyleRange(textStartPosition, message
						.length(), color, chatConsole.inputText.getBackground()));
	}

	protected void decorateLinks(ChatEvent event, String message,
			int textStartPosition) {
		if (event.getType() != ChatType.OUTBOUND) {
			List<int[]> linkRanges = new ArrayList<int[]>(5);

			// First check http://,https://,www.
			int startIndex = message.indexOf("http://");
			if (startIndex == -1) {
				startIndex = message.indexOf("https://");
				if (startIndex == -1) {
					startIndex = message.indexOf("www.");
				}
			}
			while (startIndex != -1 && startIndex < message.length()) {
				int endIndex = startIndex + 1;
				while (endIndex < message.length()) {

					// On ICS servers line breaks follow the convention \n\\
					// This code underlines links that have line breaks in
					// them.
					if (message.charAt(endIndex) == '\n'
							&& message.length() > endIndex + 1
							&& message.charAt(endIndex + 1) == '\\') {
						endIndex += 2;

						// Move past the white space and then continue on
						// with
						// the
						// main loop.
						while (endIndex < message.length()
								&& (Character.isWhitespace(message
										.charAt(endIndex)))) {
							endIndex++;
						}
						continue;
					} else if (Character.isWhitespace(message.charAt(endIndex))) {
						break;
					}
					endIndex++;
				}

				if (message.charAt(endIndex - 1) == '.') {
					endIndex--;
				}

				linkRanges.add(new int[] { startIndex, endIndex });

				startIndex = message.indexOf("http://", endIndex + 1);
				if (startIndex == -1) {
					startIndex = message.indexOf("https://", endIndex + 1);
					if (startIndex == -1) {
						startIndex = message.indexOf("www.", endIndex + 1);
					}
				}
			}

			// Next check ending with .com,.org,.edu
			int endIndex = message.indexOf(".com");
			if (endIndex == -1 || isInRanges(startIndex, linkRanges)) {
				endIndex = message.indexOf(".org");
				if (endIndex == -1 || isInRanges(startIndex, linkRanges)) {
					endIndex = message.indexOf(".edu");
				}
			}
			if (endIndex != -1 && isInRanges(startIndex, linkRanges)) {
				endIndex = -1;
			}
			int linkEnd = endIndex + 4;
			while (endIndex != -1) {
				startIndex = endIndex--;
				while (startIndex >= 0) {
					// On ICS servers line breaks follow the convention \n\\
					// This code underlines links that have line breaks in
					// them.
					if (Character.isWhitespace(message.charAt(startIndex))) {
						break;
					}
					startIndex--;
				}

				// Filter out emails.
				int atIndex = message.indexOf("@", startIndex);
				if (atIndex == -1 || atIndex > linkEnd) {
					linkRanges.add(new int[] { startIndex + 1, linkEnd });
				}

				endIndex = message.indexOf(".com", linkEnd + 1);
				if (endIndex == -1 || isInRanges(startIndex, linkRanges)) {
					endIndex = message.indexOf(".org", linkEnd + 1);
					if (endIndex == -1 || isInRanges(startIndex, linkRanges)) {
						endIndex = message.indexOf(".com", linkEnd + 1);
					}
				}
				if (endIndex != -1 && isInRanges(startIndex, linkRanges)) {
					endIndex = -1;
				}
				linkEnd = endIndex + 4;
			}

			// add all the ranges that were found.
			for (int[] linkRange : linkRanges) {
				Color underlineColor = chatConsole.getPreferences().getColor(
						CHAT_LINK_UNDERLINE_COLOR);
				StyleRange range = new StyleRange(textStartPosition
						+ linkRange[0], linkRange[1] - linkRange[0],
						underlineColor, chatConsole.inputText.getBackground());
				range.underline = true;
				chatConsole.inputText.setStyleRange(range);
			}
		}

	}

	protected void decorateQuotes(ChatEvent event, String message,
			int textStartPosition) {
		if (event.getType() != ChatType.OUTBOUND) {
			List<int[]> quotedRanges = new ArrayList<int[]>(5);

			int quoteIndex = message.indexOf("\"");
			if (quoteIndex == -1) {
				quoteIndex = message.indexOf("'");
			}

			while (quoteIndex != -1) {
				int endQuote = message.indexOf("\"", quoteIndex + 1);
				if (endQuote == -1) {
					endQuote = message.indexOf("'", quoteIndex + 1);
				}

				if (endQuote == -1) {
					break;
				} else {
					if (quoteIndex + 1 != endQuote) {

						// If there is a newline between the quotes ignore
						// it.
						int newLine = message.indexOf("\n", quoteIndex);

						// If there is just one character and the a space
						// after
						// the
						// first quote ignore it.
						boolean isASpaceTwoCharsAfterQuote = message
								.charAt(quoteIndex + 2) == ' ';

						// If the quotes dont match ignore it.
						boolean doQuotesMatch = message.charAt(quoteIndex) == message
								.charAt(endQuote);

						if (!(newLine > quoteIndex && newLine < endQuote)
								&& !isASpaceTwoCharsAfterQuote && doQuotesMatch) {
							quotedRanges.add(new int[] { quoteIndex + 1,
									endQuote });

						}
					}
				}

				quoteIndex = message.indexOf("\"", endQuote + 1);
				if (quoteIndex == -1) {
					quoteIndex = message.indexOf("'", endQuote + 1);
				}
			}

			for (int[] quotedRange : quotedRanges) {
				Color underlineColor = chatConsole.getPreferences().getColor(
						CHAT_QUOTE_UNDERLINE_COLOR);
				StyleRange range = new StyleRange(textStartPosition
						+ quotedRange[0], quotedRange[1] - quotedRange[0],
						underlineColor, chatConsole.inputText.getBackground());
				range.underline = true;
				chatConsole.inputText.setStyleRange(range);
			}
		}

	}

	public void dispose() {

		if (connector != null) {
			connector.getChatService().removeChatServiceListener(
					chatServiceListener);
			connector.addConnectorListener(connectorListener);
			connectorListener = null;
			connector = null;
		}

		removeListenersTiedToChatConsole();

		if (itemChangedListeners != null) {
			itemChangedListeners.clear();
			itemChangedListeners = null;
		}

		if (awayList != null) {
			awayList.clear();
			awayList = null;
		}

		if (eventsWhileBeingReparented != null) {
			eventsWhileBeingReparented.clear();
			eventsWhileBeingReparented = null;
		}

		LOG.debug("Disposed ChatConsoleController");
	}

	/**
	 * Should be invoked when the title or closeability changes.
	 */
	protected void fireItemChanged() {
		for (ItemChangedListener listener : itemChangedListeners) {
			listener.itemStateChanged();
		}
	}

	public ChatConsole getChatConsole() {
		return chatConsole;
	}

	public Connector getConnector() {
		return connector;
	}

	/**
	 * Returns an Image icon that can be used to represent this controller.
	 */
	public Image getIconImage() {
		return null;
	}

	public List<ItemChangedListener> getItemChangedListeners() {
		return itemChangedListeners;
	}

	public abstract String getName();

	public RaptorPreferenceStore getPreferences() {
		return Raptor.getInstance().getPreferences();
	}

	public abstract Quadrant getPreferredQuadrant();

	public String getPrependText() {
		return "";
	}

	public abstract String getPrompt();

	public String getSourceOfLastTellReceived() {
		return sourceOfLastTellReceived;
	}

	/**
	 * Returns the title. THe current format is connector.shortName([CONNECTOR
	 * STATUS IF NOT CONNECTED]getName()).
	 */
	public String getTitle() {
		if (connector == null) {
			return "Error";
		} else if (connector.isConnecting()) {
			return connector.getShortName() + "(Connecting-" + getName() + ")";
		} else if (connector.isConnected()) {
			return connector.getShortName() + "(" + getName() + ")";
		} else {
			return connector.getShortName() + "(Disconnected-" + getName()
					+ ")";
		}
	}

	public boolean hasUnseenText() {
		return hasUnseenText;
	}

	public void init() {
		addInputTextKeyListeners();
		addMouseListeners();
		registerForChatEvents();
		adjustAwayButtonEnabled();
		chatConsole.getOutputText().setText(getPrependText());
		setCaretToOutputTextEnd();
	}

	public abstract boolean isAcceptingChatEvent(ChatEvent inboundEvent);

	public abstract boolean isAwayable();

	public abstract boolean isCloseable();

	public boolean isIgnoringActions() {
		boolean result = false;
		if (isBeingReparented || chatConsole == null
				|| chatConsole.isDisposed()) {
			LOG
					.debug(
							"isBeingReparented invoked. The exception is thrown just to debug the stack trace.",
							new Exception());
			result = true;
		}
		return result;
	}

	protected boolean isInRanges(int location, List<int[]> ranges) {
		boolean result = false;
		for (int[] range : ranges) {
			if (location >= range[0] && location <= range[1]) {
				result = true;
				break;
			}
		}
		return result;
	}

	public abstract boolean isPrependable();

	public abstract boolean isSearchable();

	public boolean isSoundDisabled() {
		return isSoundDisabled;
	}

	public void onActivate() {
		chatConsole.getDisplay().timerExec(100, new Runnable() {
			public void run() {

				onForceAutoScroll();
			}
		});
	}

	public void onAppendChatEventToInputText(ChatEvent event) {

		if (!ignoreAwayList && event.getType() == ChatType.TELL
				|| event.getType() == ChatType.PARTNER_TELL) {
			awayList.add(event);
			adjustAwayButtonEnabled();
		}

		String appendText = null;
		int startIndex = 0;

		// synchronize on chatConsole so the scrolling will be handled
		// appropriately if there are multiple events being
		// published at the same time.
		synchronized (chatConsole) {
			if (chatConsole.isDisposed()) {
				return;
			}

			boolean isScrollBarAtMax = false;
			ScrollBar scrollbar = chatConsole.inputText.getVerticalBar();
			if (scrollbar != null && scrollbar.isVisible()) {
				isScrollBarAtMax = scrollbar.getMaximum() == scrollbar
						.getSelection()
						+ scrollbar.getThumb();
			}

			String messageText = event.getMessage();
			String date = "";
			if (Raptor.getInstance().getPreferences().getBoolean(
					CHAT_TIMESTAMP_CONSOLE)) {
				SimpleDateFormat format = new SimpleDateFormat(Raptor
						.getInstance().getPreferences().getString(
								CHAT_TIMESTAMP_CONSOLE_FORMAT));
				date = format.format(new Date(event.getTime()));
			}

			appendText = (chatConsole.inputText.getCharCount() == 0 ? "" : "\n")
					+ date + messageText;

			chatConsole.inputText.append(appendText);
			startIndex = chatConsole.inputText.getCharCount()
					- appendText.length();

			if (isScrollBarAtMax
					&& ((chatConsole.inputText.getSelection().y - chatConsole.inputText
							.getSelection().x) == 0)) {
				onForceAutoScroll();
			}
		}

		onDecorateInputText(event, appendText, startIndex);
		reduceInputTextIfNeeded();

	}

	public void onAppendOutputText(String string) {

		chatConsole.outputText.append(string);
		chatConsole.outputText.setSelection(chatConsole.outputText
				.getCharCount());
		setCaretToOutputTextEnd();

	}

	public void onAway() {

		ignoreAwayList = true;
		onAppendChatEventToInputText(new ChatEvent(null, ChatType.OUTBOUND,
				"Direct tells you missed while you were away:"));
		for (ChatEvent event : awayList) {
			onAppendChatEventToInputText(event);
		}
		awayList.clear();
		ignoreAwayList = false;
		adjustAwayButtonEnabled();

	}

	public void onChatEvent(ChatEvent event) {

		if (event.getType() == ChatType.TELL) {
			sourceOfLastTellReceived = event.getSource();
		}

		if (isAcceptingChatEvent(event)) {
			onAppendChatEventToInputText(event);
			if (!isIgnoringActions()) {
				playSounds(event);
			}
		}

	}

	protected void onDecorateInputText(final ChatEvent event,
			final String message, final int textStartPosition) {

		decorateForegroundColor(event, message, textStartPosition);
		decorateQuotes(event, message, textStartPosition);
		decorateLinks(event, message, textStartPosition);

	}

	public void onForceAutoScroll() {
		if (this.isIgnoringActions()) {
			return;
		}

		chatConsole.inputText.setCaretOffset(chatConsole.inputText
				.getCharCount());
		chatConsole.inputText.setSelection(new Point(chatConsole.inputText
				.getCharCount(), chatConsole.inputText.getCharCount()));

	}

	public void onPassivate() {
	}

	public void onPostReparent() {

		// Add all the ChatEvents missed.
		synchronized (eventsWhileBeingReparented) {

			// If a chat event is received while adding missed events it will be
			// missed.
			for (ChatEvent event : eventsWhileBeingReparented) {
				onChatEvent(event);
			}
			eventsWhileBeingReparented.clear();
			isBeingReparented = false;
		}
		awayList.clear();

		addInputTextKeyListeners();
		addMouseListeners();
		adjustAwayButtonEnabled();
		setCaretToOutputTextEnd();
		onForceAutoScroll();
	}

	public void onPreReparent() {
		removeListenersTiedToChatConsole();
		isBeingReparented = true;
	}

	public void onSave() {
		if (isIgnoringActions()) {
			return;
		}
		FileDialog fd = new FileDialog(chatConsole.getShell(), SWT.SAVE);
		fd.setText("Save Console Output.");
		fd.setFilterPath("");
		String[] filterExt = { "*.txt", "*.*" };
		fd.setFilterExtensions(filterExt);
		final String selected = fd.open();

		if (selected != null) {
			chatConsole.getDisplay().asyncExec(new Runnable() {
				public void run() {
					FileWriter writer = null;
					try {
						writer = new FileWriter(selected);
						writer.append("Raptor console log created on "
								+ new Date() + "\n");
						int i = 0;
						while (i < chatConsole.getInputText().getCharCount() - 1) {
							int endIndex = i + TEXT_CHUNK_SIZE;
							if (endIndex >= chatConsole.getInputText()
									.getCharCount()) {
								endIndex = i
										+ (chatConsole.getInputText()
												.getCharCount() - i) - 1;
							}
							String string = chatConsole.getInputText().getText(
									i, endIndex);
							writer.append(string);
							i = endIndex;
						}
						writer.flush();
					} catch (Throwable t) {
						LOG.error("Error writing file: " + selected, t);
					} finally {
						if (writer != null) {
							try {
								writer.close();
							} catch (IOException ioe) {
							}
						}
					}
				}
			});
		}

	}

	protected void onSearch() {
		if (!isIgnoringActions()) {

			chatConsole.getDisplay().asyncExec(new Runnable() {
				public void run() {
					String searchString = chatConsole.outputText.getText();
					if (StringUtils.isBlank(searchString)) {
						MessageBox box = new MessageBox(chatConsole.getShell(),
								SWT.ICON_INFORMATION | SWT.OK);
						box
								.setMessage("You must enter text in the input field to search on.");
						box.setText("Alert");
						box.open();
					} else {
						boolean foundText = false;
						searchString = searchString.toUpperCase();
						int start = chatConsole.inputText.getCaretOffset();

						if (start >= chatConsole.inputText.getCharCount()) {
							start = chatConsole.inputText.getCharCount() - 1;
						} else if (start - searchString.length() + 1 >= 0) {
							String text = chatConsole.inputText.getText(start
									- searchString.length(), start - 1);
							if (text.equalsIgnoreCase(searchString)) {
								start -= searchString.length();
							}
						}

						while (start > 0) {
							int charsBack = 0;
							if (start - TEXT_CHUNK_SIZE > 0) {
								charsBack = TEXT_CHUNK_SIZE;
							} else {
								charsBack = start;
							}

							String stringToSearch = chatConsole.inputText
									.getText(start - charsBack, start)
									.toUpperCase();
							int index = stringToSearch
									.lastIndexOf(searchString);
							if (index != -1) {
								int textStart = start - charsBack + index;
								chatConsole.inputText.setSelection(textStart,
										textStart + searchString.length());
								foundText = true;
								break;
							}
							start -= charsBack;
						}

						if (!foundText) {
							MessageBox box = new MessageBox(chatConsole
									.getShell(), SWT.ICON_INFORMATION | SWT.OK);
							box.setMessage("Could not find any occurances of '"
									+ searchString + "'.");
							box.setText("Alert");
							box.open();
						}
					}
				}
			});
		}
	}

	public void onSendOutputText() {
		connector.sendMessage(chatConsole.outputText.getText());
		chatConsole.outputText.setText(getPrependText());
		setCaretToOutputTextEnd();
		awayList.clear();
		adjustAwayButtonEnabled();

	}

	protected void playSounds(ChatEvent event) {
		System.err.println("play sound " + isSoundDisabled);
		if (!isSoundDisabled) {
			if (event.getType() == ChatType.TELL
					|| event.getType() == ChatType.PARTNER_TELL) {
				SoundService.getInstance().playSound("chat");
			}
		}
	}

	protected void reduceInputTextIfNeeded() {

		int charCount = chatConsole.inputText.getCharCount();
		if (charCount > Raptor.getInstance().getPreferences().getInt(
				CHAT_MAX_CONSOLE_CHARS)) {
			LOG.info("Cleaning chat console");
			long startTime = System.currentTimeMillis();
			int cleanTo = (int) (charCount * CLEAN_PERCENTAGE);
			chatConsole.inputText.getContent().replaceTextRange(0, cleanTo, "");
			LOG.info("Cleaned console in "
					+ (System.currentTimeMillis() - startTime));
		}

	}

	protected void registerForChatEvents() {
		connector.getChatService().addChatServiceListener(chatServiceListener);
	}

	public void removeItemChangedListener(ItemChangedListener listener) {
		itemChangedListeners.remove(listener);
	}

	protected void removeListenersTiedToChatConsole() {
		if (!chatConsole.isDisposed()) {
			chatConsole.outputText.removeKeyListener(functionKeyListener);
			chatConsole.outputText.removeKeyListener(outputHistoryListener);
			chatConsole.outputText.removeKeyListener(outputKeyListener);
			chatConsole.inputText.removeKeyListener(inputTextKeyListener);
			chatConsole.inputText.removeKeyListener(functionKeyListener);
			chatConsole.inputText.removeKeyListener(outputHistoryListener);
			chatConsole.inputText.removeMouseListener(inputTextClickListener);
		}
	}

	protected void setCaretToOutputTextEnd() {
		if (!isIgnoringActions()) {
			chatConsole.outputText.forceFocus();
			getChatConsole().getOutputText().setSelection(
					getChatConsole().getOutputText().getCharCount());
		}
	}

	public void setChatConsole(ChatConsole chatConsole) {
		this.chatConsole = chatConsole;
	}

	public void setHasUnseenText(boolean hasUnseenText) {
		this.hasUnseenText = hasUnseenText;
	}

	public void setItemChangedListeners(
			List<ItemChangedListener> itemChangedListeners) {
		this.itemChangedListeners = itemChangedListeners;
	}

	public void setSoundDisabled(boolean isSoundDisabled) {
		System.err.println("setSoundDisabled" + isSoundDisabled);
		this.isSoundDisabled = isSoundDisabled;
	}

	public void setSourceOfLastTellReceived(String sourceOfLastTellReceived) {
		this.sourceOfLastTellReceived = sourceOfLastTellReceived;
	}
}
