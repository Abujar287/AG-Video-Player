#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ -n "$CYGWIN" ] ; then
  case "$CYGWIN" in
    *noglob* ) ;;
    * ) set -o noglob ;;
  esac
fi

# Set variable for backward compatibility
CYGWIN_HOME="$CYGWIN"
unset CYGWIN

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses this
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
    if ! command -v java >/dev/null 2>&1 ; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." 2>&1
        exit 1
    fi
fi

# Increase the maximum heap size if the user hasn't already set JVM_OPTS.
if [ -n "$DEFAULT_JVM_OPTS" ] ; then
    DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"
fi

# For Cygwin, switch paths to Windows format before running java
if [ "$cygwin" = "true" ] ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    JAVACMD=`cygpath --mixed "$JAVACMD"`

    # We cannot use the default 'cygpath' parser for user paths because
    # of a bug in Cygwin: http://cygwin.com/ml/cygwin/apps-inspect/2016-03/msg00116.html
    # Instead, we'll use a custom awk script to parse user paths.
    if [ -n "$JAVA_HOME" ] ; then
        JAVA_HOME=`cygpath --path --mixed "$JAVA_HOME"`
    fi
fi

# Escape an argument to be safely quoted for a shell.
app_path=`dirname "$0"`
exec "$JAVACMD" -jar "$app_path/gradle/wrapper/gradle-wrapper.jar" "$@"
