/*
 * Copyright 2017 the original author or authors.
 *
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
 */

package org.gradle.caching.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.server.http.HttpBuildCache
import org.junit.Rule
import spock.lang.Unroll

class FinalizeBuildCacheConfigurationBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    private static final SOME_CREDENTIALS =
        """
            remote.credentials {
                username = "user"
                password = "pass"
            }
        """

    private static final NO_CREDENTIALS = ''

    private static final INCOMPLETE_CREDENTIALS =
        """
            remote.credentials {
                username = "user"
            }
        """

    @Rule
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Rule
    HttpBuildCache httpBuildCache = new HttpBuildCache(testDirectoryProvider)

    @Unroll
    def "local build cache configuration is exposed"() {
        given:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        def directory = cacheDir.absoluteFile.toURI().toString()
        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    enabled = $enabled
                    directory = '$directory'
                    push = $push
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        result.enabled == true

        result.local.className == 'org.gradle.caching.local.DirectoryBuildCache'
        result.local.config.directory == directory
        result.local.displayName == 'Directory'
        result.local.enabled == enabled
        result.local.push == push

        result.remote == null

        where:
        push  | enabled
        true  | true
        false | false
    }

    @Unroll
    def "remote build cache configuration is exposed"() {
        given:
        httpBuildCache.start()
        def url = "${httpBuildCache.uri}/"
        settingsFile << """
            buildCache {  
                local {
                    enabled = $enabled 
                }
                remote(org.gradle.caching.http.HttpBuildCache) {
                    enabled = $enabled 
                    url = "$url"   
                    push = $push 
                    $credentials
                }
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        result.enabled == true

        result.remote.className == 'org.gradle.caching.http.HttpBuildCache'
        result.remote.config.url == url
        result.remote.config.authenticated == authenticated
        result.remote.displayName == 'HTTP'
        result.remote.enabled == enabled
        result.remote.push == push

        result.local.enabled == enabled

        where:
        authenticated | credentials            | push  | enabled
        'true'        | SOME_CREDENTIALS       | true  | true
        'false'       | NO_CREDENTIALS         | false | false
        'false'       | INCOMPLETE_CREDENTIALS | false | false
    }

    def "custom build cache connector configuration is exposed"() {
        given:
        def directory = 'someLocation'
        def displayName = 'CustomBuildCache Desc'
        settingsFile << """
            class VisibleNoOpBuildCacheService implements BuildCacheService {
                @Override boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException { false }
                @Override void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {}
                @Override String getDescription() { "NO-OP build cache" }
                @Override void close() throws IOException {}
            }
            class CustomBuildCache extends AbstractBuildCache {
                @Override Map<String, String> getConfigDescription() { [directory: '$directory'] }
                @Override String getDisplayName() { '$displayName' }
            }
            class CustomBuildCacheFactory implements BuildCacheServiceFactory<CustomBuildCache> {
                @Override BuildCacheService createBuildCacheService(CustomBuildCache configuration) { new VisibleNoOpBuildCacheService() }
            }
            
            buildCache {
                registerBuildCacheService(CustomBuildCache, CustomBuildCacheFactory)
                
                local(CustomBuildCache)
            }
        """
        executer.withBuildCacheEnabled()

        when:
        succeeds("help")

        then:
        def result = result()
        result.enabled == true

        result.local.enabled == true
        result.local.className == 'CustomBuildCache'
        result.local.config.directory == directory
        result.local.displayName == displayName

    }

    def "null build cache configurations are exposed when build cache is not enabled"() {
        when:
        succeeds("help")

        then:
        def result = result()
        result.enabled == false
        result.local == null
        result.remote == null
    }

    Map<String, ?> result() {
        buildOperations.result("Finalize build cache configuration")
    }

}