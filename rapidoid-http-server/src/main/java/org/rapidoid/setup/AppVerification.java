package org.rapidoid.setup;

/*
 * #%L
 * rapidoid-http-server
 * %%
 * Copyright (C) 2014 - 2016 Nikolche Mihajlovski and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.rapidoid.RapidoidThing;
import org.rapidoid.annotation.Authors;
import org.rapidoid.annotation.Since;
import org.rapidoid.commons.Arr;
import org.rapidoid.commons.Env;
import org.rapidoid.config.Conf;
import org.rapidoid.log.Log;
import org.rapidoid.u.U;
import org.rapidoid.util.Msc;

@Authors("Nikolche Mihajlovski")
@Since("5.2.3")
public class AppVerification extends RapidoidThing {

	static void selfVerify(String[] args) {
		if (Arr.contains(args, "docker-self-verify")) {
			dockerSelfVerify();
			verifyNotInitialized();
		}

		if (!Msc.isInsideTest()) {
			verifyNotInitialized();
		}
	}

	private static void verifyNotInitialized() {
		U.must(!Conf.isInitialized(), "The configuration shouldn't be initialized yet!");
	}

	private static void dockerSelfVerify() {
		U.must(Msc.dockerized(), "Docker environment couldn't be detected!");
		U.must("/app".equals(Env.root()), "The default root path for Docker environment must be '/app'!");

		Log.info("Docker environment was verified!");
	}

}
