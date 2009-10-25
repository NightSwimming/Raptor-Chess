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
package raptor.util;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import raptor.Raptor;
import raptor.action.RaptorAction;
import raptor.action.RaptorActionFactory;

public class CreateClassActions {
	@SuppressWarnings("unchecked")
	public static void main(String args[]) throws Exception {
		Class[] classes = ReflectionUtils.getClasses("raptor.action.chat");
		for (Class clazz : classes) {
			RaptorAction action = (RaptorAction) clazz.newInstance();
			Properties properties = RaptorActionFactory.save(action);

			File file = new File(Raptor.RESOURCES_DIR + "/actions/"
					+ action.getName() + ".properties");

			if (!file.exists()) {
				properties.store(new FileOutputStream(file),
						"Raptor System Action");
				System.err.println("Created " + file);
			}
		}

		classes = ReflectionUtils.getClasses("raptor.action.game");
		for (Class clazz : classes) {
			RaptorAction action = (RaptorAction) clazz.newInstance();
			Properties properties = RaptorActionFactory.save(action);

			File file = new File(Raptor.RESOURCES_DIR + "/actions/"
					+ action.getName() + ".properties");

			if (!file.exists()) {
				properties.store(new FileOutputStream(file),
						"Raptor System Action");
				System.err.println("Created " + file);
			}
		}
	}
}
