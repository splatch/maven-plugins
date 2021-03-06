package org.apache.maven.plugin.ear.it;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import junit.framework.TestCase;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.apache.maven.plugin.ear.util.ResourceEntityResolver;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Base class for ear test cases.
 *
 * @author <a href="snicoll@apache.org">Stephane Nicoll</a>
 * @version $Id$
 */
public abstract class AbstractEarPluginIT
    extends TestCase
{

    protected final String FINAL_NAME_PREFIX = "maven-ear-plugin-test-";

    protected final String FINAL_NAME_SUFFIX = "-99.0";

    /**
     * The base directory.
     */
    private File basedir;

    /**
     * Test repository directory.
     */
    protected File localRepositoryDir = new File( getBasedir().getAbsolutePath(), "target/test-classes/m2repo" );

    protected File settingsFile = new File( getBasedir().getAbsolutePath(), "target/test-classes/settings.xml" );


    /**
     * Execute the EAR plugin for the specified project.
     *
     * @param projectName the name of the project
     * @param properties  extra properties to be used by the embedder
     * @return the base directory of the project
     * @throws Exception if an error occurred
     */
    protected File executeMojo( final String projectName, final Properties properties, boolean expectNoError )
        throws Exception
    {
        System.out.println( "  Building: " + projectName );

        File testDir = getTestDir( projectName );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        // Let's add alternate settings.xml setting so that the latest dependencies are used
        verifier.getCliOptions().add( "-s \"" + settingsFile.getAbsolutePath() + "\"" );
        verifier.getCliOptions().add( "-X" );
        verifier.localRepo = localRepositoryDir.getAbsolutePath();

        // On linux and macOSX, an exception is thrown if a build failure occurs underneath
        try
        {
            verifier.executeGoal( "package" );
        }
        catch ( VerificationException e )
        {
            //@TODO needs to be handled nicely in the verifier
            if ( expectNoError || e.getMessage().indexOf( "Exit code was non-zero" ) == -1 )
            {
                throw e;
            }
        }

        // If no error is expected make sure that error logs are free
        if ( expectNoError )
        {
            verifier.verifyErrorFreeLog();
        }
        verifier.resetStreams();
        return testDir;
    }

    /**
     * Execute the EAR plugin for the specified project.
     *
     * @param projectName the name of the project
     * @param properties  extra properties to be used by the embedder
     * @return the base directory of the project
     * @throws Exception if an error occurred
     */
    protected File executeMojo( final String projectName, final Properties properties )
        throws Exception
    {
        return executeMojo( projectName, properties, true );
    }


    /**
     * Executes the specified projects and asserts the given artifacts.
     *
     * @param projectName               the project to test
     * @param expectedArtifacts         the list of artifacts to be found in the EAR archive
     * @param artifactsDirectory        whether the artifact is an exploded artifactsDirectory or not
     * @param testDeploymentDescriptors whether we should test deployment descriptors
     * @return the base directory of the project
     * @throws Exception
     */
    protected File doTestProject( final String projectName, final String[] expectedArtifacts,
                                  final boolean[] artifactsDirectory, boolean testDeploymentDescriptors )
        throws Exception
    {
        final File baseDir = executeMojo( projectName, new Properties() );
        assertEarArchive( baseDir, projectName );
        assertEarDirectory( baseDir, projectName );

        assertArchiveContent( baseDir, projectName, expectedArtifacts, artifactsDirectory );

        if ( testDeploymentDescriptors )
        {
            assertDeploymentDescriptors( baseDir, projectName );
        }

        return baseDir;

    }

    /**
     * Executes the specified projects and asserts the given artifacts. Assert the
     * deployment descriptors are valid
     *
     * @param projectName        the project to test
     * @param expectedArtifacts  the list of artifacts to be found in the EAR archive
     * @param artifactsDirectory whether the artifact is an exploded artifactsDirectory or not
     * @return the base directory of the project
     * @throws Exception
     */
    protected File doTestProject( final String projectName, final String[] expectedArtifacts,
                                  final boolean[] artifactsDirectory )
        throws Exception
    {
        return doTestProject( projectName, expectedArtifacts, artifactsDirectory, true );

    }

    /**
     * Executes the specified projects and asserts the given artifacts as
     * artifacts (non directory)
     *
     * @param projectName               the project to test
     * @param expectedArtifacts         the list of artifacts to be found in the EAR archive
     * @param testDeploymentDescriptors whether we should test deployment descriptors
     * @return the base directory of the project
     * @throws Exception
     */
    protected File doTestProject( final String projectName, final String[] expectedArtifacts,
                                  boolean testDeploymentDescriptors )
        throws Exception
    {
        return doTestProject( projectName, expectedArtifacts, new boolean[expectedArtifacts.length] );
    }

    /**
     * Executes the specified projects and asserts the given artifacts as
     * artifacts (non directory). Assert the deployment descriptors are valid
     *
     * @param projectName       the project to test
     * @param expectedArtifacts the list of artifacts to be found in the EAR archive
     * @return the base directory of the project
     * @throws Exception
     */
    protected File doTestProject( final String projectName, final String[] expectedArtifacts )
        throws Exception
    {
        return doTestProject( projectName, expectedArtifacts, true );
    }

    protected void assertEarArchive( final File baseDir, final String projectName )
    {
        assertTrue( "EAR archive does not exist", getEarArchive( baseDir, projectName ).exists() );
    }

    protected void assertEarDirectory( final File baseDir, final String projectName )
    {
        assertTrue( "EAR archive directory does not exist", getEarDirectory( baseDir, projectName ).exists() );
    }

    protected File getTargetDirectory( final File basedir )
    {
        return new File( basedir, "target" );
    }

    protected File getEarArchive( final File baseDir, final String projectName )
    {
        return new File( getTargetDirectory( baseDir ), buildFinalName( projectName ) + ".ear" );
    }

    protected File getEarDirectory( final File baseDir, final String projectName )
    {
        return new File( getTargetDirectory( baseDir ), buildFinalName( projectName ) );
    }

    protected String buildFinalName( final String projectName )
    {
        return FINAL_NAME_PREFIX + projectName + FINAL_NAME_SUFFIX;
    }

    protected void assertArchiveContent( final File baseDir, final String projectName, final String[] artifactNames,
                                         final boolean[] artifactsDirectory )
    {
        // sanity check
        assertEquals( "Wrong parameter, artifacts mismatch directory flags", artifactNames.length,
                      artifactsDirectory.length );

        File dir = getEarDirectory( baseDir, projectName );

        // Let's build the expected directories sort list
        final List expectedDirectories = new ArrayList();
        for ( int i = 0; i < artifactsDirectory.length; i++ )
        {
            if ( artifactsDirectory[i] )
            {
                expectedDirectories.add( new File( dir, artifactNames[i] ) );
            }
        }

        final List actualFiles = buildArchiveContentFiles( dir, expectedDirectories );
        assertEquals( "Artifacts mismatch " + actualFiles, artifactNames.length, actualFiles.size() );
        for ( int i = 0; i < artifactNames.length; i++ )
        {
            String artifactName = artifactNames[i];
            final boolean isDirectory = artifactsDirectory[i];
            File expectedFile = new File( dir, artifactName );

            assertEquals( "Artifact[" + artifactName + "] not in the right form (exploded/archive", isDirectory,
                          expectedFile.isDirectory() );
            assertTrue( "Artifact[" + artifactName + "] not found in ear archive",
                        actualFiles.contains( expectedFile ) );

        }
    }

    protected List buildArchiveContentFiles( final File baseDir, final List expectedDirectories )
    {
        final List result = new ArrayList();
        addFiles( baseDir, result, expectedDirectories );

        return result;
    }

    private void addFiles( final File directory, final List files, final List expectedDirectories )
    {
        File[] result = directory.listFiles( new FilenameFilter()
        {
            public boolean accept( File dir, String name )
            {
                return !name.equals( "META-INF" );
            }

        } );

        /*
           Kinda complex. If we found a file, we always add it to the list
           of files. If a directory is within the expectedDirectories short
           list we add it but we don't add it's content. Otherwise, we don't
           add the directory *BUT* we browse it's content
         */
        for ( int i = 0; i < result.length; i++ )
        {
            File file = result[i];
            if ( file.isFile() )
            {
                files.add( file );
            }
            else if ( expectedDirectories.contains( file ) )
            {
                files.add( file );
            }
            else
            {
                addFiles( file, files, expectedDirectories );
            }
        }
    }

    protected File getBasedir()
    {
        if ( basedir != null )
        {
            return basedir;
        }

        final String basedirString = System.getProperty( "basedir" );
        if ( basedirString == null )
        {
            basedir = new File( "" );
        }
        else
        {
            basedir = new File( basedirString );
        }
        return basedir;
    }

    protected File getTestDir( String projectName )
        throws IOException
    {
        return ResourceExtractor.simpleExtractResources( getClass(), "/projects/" + projectName );
    }

    // Generated application.xml stuff

    /**
     * Asserts that the deployment descriptors have been generated successfully.
     * <p/>
     * This test assumes that deployment descriptors are located in the
     * <tt>expected-META-INF</tt> directory of the project. Note that the
     * <tt>MANIFEST.mf</tt> file is ignored and is not tested.
     *
     * @param baseDir     the directory of the tested project
     * @param projectName the name of the project
     */
    protected void assertDeploymentDescriptors( final File baseDir, final String projectName )
        throws IOException
    {
        final File earDirectory = getEarDirectory( baseDir, projectName );
        final File[] actualDeploymentDescriptors = getDeploymentDescriptors( new File( earDirectory, "META-INF" ) );
        final File[] expectedDeploymentDescriptors =
            getDeploymentDescriptors( new File( baseDir, "expected-META-INF" ) );

        if ( expectedDeploymentDescriptors == null )
        {
            assertNull( "No deployment descriptor was expected", actualDeploymentDescriptors );
        }
        else
        {
            assertNotNull( "Missing deployment descriptor", actualDeploymentDescriptors );

            // Make sure we have the same number of files
            assertEquals( "Number of Deployment descriptor(s) mismatch", expectedDeploymentDescriptors.length,
                          actualDeploymentDescriptors.length );

            // Sort the files so that we have the same behavior here
            Arrays.sort( expectedDeploymentDescriptors );
            Arrays.sort( actualDeploymentDescriptors );

            for ( int i = 0; i < expectedDeploymentDescriptors.length; i++ )
            {
                File expectedDeploymentDescriptor = expectedDeploymentDescriptors[i];
                File actualDeploymentDescriptor = actualDeploymentDescriptors[i];

                assertEquals( "File name mismatch", expectedDeploymentDescriptor.getName(),
                              actualDeploymentDescriptor.getName() );

                try
                {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setValidating( true );
                    DocumentBuilder docBuilder = dbf.newDocumentBuilder();
                    docBuilder.setEntityResolver( new ResourceEntityResolver() );
                    docBuilder.setErrorHandler( new DefaultHandler() );

                    // Make sure that it matches even if the elements are not in
                    // the exact same order
                    final Diff myDiff =
                        new Diff( docBuilder.parse( expectedDeploymentDescriptor ),
                                  docBuilder.parse( actualDeploymentDescriptor ) );
                    myDiff.overrideElementQualifier( new RecursiveElementNameAndTextQualifier() );
                    XMLAssert.assertXMLEqual(
                        "Wrong deployment descriptor generated for[" + expectedDeploymentDescriptor.getName() + "]",
                        myDiff, true );
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                    fail( "Could not assert deployment descriptor " + e.getMessage() );
                }
            }
        }
    }

    private File[] getDeploymentDescriptors( final File ddDirectory )
    {
        return ddDirectory.listFiles( new FilenameFilter()
        {
            public boolean accept( File dir, String name )
            {
                return !name.equalsIgnoreCase( "manifest.mf" );
            }
        } );
    }
}
