/**
 * New BSD License
 * http://www.opensource.org/licenses/bsd-license.php
 * Copyright (c) 2009, RaptorProject (http://code.google.com/p/raptor-chess-interface/)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the RaptorProject nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package raptor.pref.page;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FontFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;

import raptor.Raptor;
import raptor.pref.PreferenceKeys;
import raptor.pref.fields.LabelButtonFieldEditor;

public class ChatConsolePage extends FieldEditorPreferencePage {

	public static final String[][] CONSOLE_CHARS = {
			{ "1/4 Million Characters", "250000" },
			{ "1/2 Million Characters", "500000" },
			{ "1 Million Characters", "1000000" },
			{ "5 Million Characters", "5000000" },
			{ "10 Million Characters", "10000000" } };

	LabelButtonFieldEditor labelButtonFieldEditor;

	public ChatConsolePage() {
		super(GRID);
		setTitle("Chat Consoles");
		setPreferenceStore(Raptor.getInstance().getPreferences());
	}

	@Override
	protected void createFieldEditors() {
		ComboFieldEditor consoleChars = new ComboFieldEditor(
				PreferenceKeys.CHAT_MAX_CONSOLE_CHARS, "Chat Console Size:",
				CONSOLE_CHARS, getFieldEditorParent());
		addField(consoleChars);

		BooleanFieldEditor addTimestamps = new BooleanFieldEditor(
				PreferenceKeys.CHAT_TIMESTAMP_CONSOLE,
				"Add Timestamps To Messages", getFieldEditorParent());
		addField(addTimestamps);

		BooleanFieldEditor underlineSingleQuotes = new BooleanFieldEditor(
				PreferenceKeys.CHAT_UNDERLINE_SINGLE_QUOTES,
				"Underline single quoted text", getFieldEditorParent());
		addField(underlineSingleQuotes);

		BooleanFieldEditor smartScroll = new BooleanFieldEditor(
				PreferenceKeys.CHAT_IS_SMART_SCROLL_ENABLED,
				"Smart Scroll (Toggles auto scroll based on the "
						+ "vertical scrolllbar position)",
				getFieldEditorParent());
		addField(smartScroll);

		addField(new BooleanFieldEditor(
				PreferenceKeys.CHAT_IS_PLAYING_CHAT_ON_PTELL,
				"Play 'chat' sound on all partner tells.",
				getFieldEditorParent()));

		addField(new BooleanFieldEditor(
				PreferenceKeys.CHAT_IS_PLAYING_CHAT_ON_PERSON_TELL,
				"Play 'chat' sound on all person tells.",
				getFieldEditorParent()));

		StringFieldEditor timestampFormat = new StringFieldEditor(
				PreferenceKeys.CHAT_TIMESTAMP_CONSOLE_FORMAT,
				"Message Timestamp Format (Advanced):", getFieldEditorParent());
		addField(timestampFormat);

		ColorFieldEditor consoleBackground = new ColorFieldEditor(
				PreferenceKeys.CHAT_CONSOLE_BACKGROUND_COLOR,
				"Console Window Background Color:", getFieldEditorParent());
		addField(consoleBackground);

		ColorFieldEditor inputTextBackground = new ColorFieldEditor(
				PreferenceKeys.CHAT_INPUT_BACKGROUND_COLOR,
				"Console Background Color:", getFieldEditorParent());
		addField(inputTextBackground);

		ColorFieldEditor outputTextForeground = new ColorFieldEditor(
				PreferenceKeys.CHAT_OUTPUT_TEXT_COLOR, "Text To Send Color:",
				getFieldEditorParent());
		addField(outputTextForeground);

		ColorFieldEditor outputTextBackground = new ColorFieldEditor(
				PreferenceKeys.CHAT_OUTPUT_BACKGROUND_COLOR,
				"Text To Send Background Color:", getFieldEditorParent());
		addField(outputTextBackground);

		ColorFieldEditor promptColor = new ColorFieldEditor(
				PreferenceKeys.CHAT_PROMPT_COLOR,
				"Text To Send Prompt Label Color:", getFieldEditorParent());
		addField(promptColor);

		ColorFieldEditor quoteUnderlineColor = new ColorFieldEditor(
				PreferenceKeys.CHAT_QUOTE_UNDERLINE_COLOR,
				"Quoted text Color:", getFieldEditorParent());
		addField(quoteUnderlineColor);

		ColorFieldEditor linkTextColor = new ColorFieldEditor(
				PreferenceKeys.CHAT_LINK_UNDERLINE_COLOR, "Links Color:",
				getFieldEditorParent());
		addField(linkTextColor);

		FontFieldEditor inputFont = new FontFieldEditor(
				PreferenceKeys.CHAT_INPUT_FONT, "Chat Console Font",
				getFieldEditorParent());
		addField(inputFont);

		FontFieldEditor outputFont = new FontFieldEditor(
				PreferenceKeys.CHAT_OUTPUT_FONT, "Text To Send Font",
				getFieldEditorParent());
		addField(outputFont);

		FontFieldEditor promptFont = new FontFieldEditor(
				PreferenceKeys.CHAT_PROMPT_FONT,
				"Text To Send Prompt Label Font", getFieldEditorParent());
		addField(promptFont);
	}
}