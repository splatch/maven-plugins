package org.apache.maven.plugins.jarsigner;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.util.Os;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Maven Jarsigner Plugin base class.
 *
 * @author <a href="cs@schulte.it">Christian Schulte</a>
 * @version $Id$
 */
public abstract class AbstractJarsignerMojo
    extends AbstractMojo
{

    /**
     * See <a href="http://java.sun.com/javase/6/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     *
     * @parameter expression="${jarsigner.verbose}" default-value="false"
     */
    private boolean verbose;

    /**
     * The maximum memory available to the JAR signer, e.g. <code>256M</code>. See <a
     * href="http://java.sun.com/javase/6/docs/technotes/tools/windows/java.html#Xms">-Xmx</a> for more details.
     * 
     * @parameter expression="${jarsigner.maxMemory}"
     */
    private String maxMemory;

    /**
     * Archive to process. If set, neither the project artifact nor any attachments are processed.
     *
     * @parameter expression="${jarsigner.archive}"
     * @optional
     */
    private File archive;

    /**
     * List of additional arguments to append to the jarsigner command line.
     *
     * @parameter expression="${jarsigner.arguments}"
     * @optional
     */
    private String[] arguments;

    /**
     * Set to {@code true} to disable the plugin.
     *
     * @parameter expression="${jarsigner.skip}" default-value="false"
     */
    private boolean skip;

    /**
     * Controls processing of project attachments.
     *
     * @parameter expression="${jarsigner.attachments}" default-value="true"
     */
    private boolean attachments;

    /**
     * The Maven project.
     *
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The path to the jarsigner we are going to use.
     */
    private String executable;

    public final void execute()
        throws MojoExecutionException
    {
        if ( !this.skip )
        {
            this.executable = getExecutable();

            if ( this.archive != null )
            {
                this.processArchive( this.archive );
            }
            else
            {
                this.processArtifact( this.project.getArtifact() );

                for ( Iterator it = this.project.getAttachedArtifacts().iterator(); it.hasNext(); )
                {
                    final Artifact artifact = (Artifact) it.next();

                    if ( this.attachments )
                    {
                        this.processArtifact( artifact );
                    }
                    else if ( this.isJarFile( artifact ) )
                    {
                        this.getLog().info( this.getMessage( "ignoringAttachment", new Object[]
                            {
                                artifact.toString()
                            } ) );

                    }
                }
            }
        }
        else
        {
            this.getLog().info( this.getMessage( "disabled", null ) );
        }
    }

    /**
     * Gets the {@code Commandline} to execute for a given Java archive taking a command line prepared for executing
     * jarsigner.
     *
     * @param archive The Java archive to get a {@code Commandline} to execute for.
     * @param commandLine A {@code Commandline} prepared for executing jarsigner without any arguments.
     *
     * @return A {@code Commandline} for executing jarsigner with {@code archive}.
     *
     * @throws NullPointerException if {@code archive} or {@code commandLine} is {@code null}.
     */
    protected abstract Commandline getCommandline( final File archive, final Commandline commandLine );

    /**
     * Gets a string representation of a {@code Commandline}.
     * <p>This method creates the string representation by calling {@code commandLine.toString()} by default.</p>
     *
     * @param commandLine The {@code Commandline} to get a string representation of.
     *
     * @return The string representation of {@code commandLine}.
     *
     * @throws NullPointerException if {@code commandLine} is {@code null}.
     */
    protected String getCommandlineInfo( final Commandline commandLine )
    {
        if ( commandLine == null )
        {
            throw new NullPointerException( "commandLine" );
        }

        return commandLine.toString();
    }

    /**
     * Checks Java language capability of an artifact.
     *
     * @param artifact The artifact to check.
     *
     * @return {@code true} if {@code artifact} is Java language capable; {@code false} if not.
     */
    private boolean isJarFile( final Artifact artifact )
    {
        return artifact != null && artifact.getFile() != null && isJarFile( artifact.getFile() );
    }

    /**
     * Checks whether the specified file is a JAR file. For our purposes, a JAR file is a (non-empty) ZIP stream with a
     * META-INF directory or some class files.
     * 
     * @param file The file to check, must not be <code>null</code>.
     * @return <code>true</code> if the file looks like a JAR file, <code>false</code> otherwise.
     */
    private boolean isJarFile( final File file )
    {
        try
        {
            // NOTE: ZipFile.getEntry() might be shorter but is several factors slower on large files

            ZipInputStream zis = new ZipInputStream( new FileInputStream( file ) );
            try
            {
                for ( ZipEntry ze; ( ze = zis.getNextEntry() ) != null; )
                {
                    if ( ze.getName().startsWith( "META-INF/" ) || ze.getName().endsWith( ".class" ) )
                    {
                        return true;
                    }
                }
            }
            finally
            {
                zis.close();
            }
        }
        catch ( Exception e )
        {
            // ignore, will fail below
        }

        return false;
    }

    /**
     * Processes a given artifact.
     *
     * @param artifact The artifact to process.
     *
     * @throws NullPointerException if {@code artifact} is {@code null}.
     * @throws MojoExecutionException if processing {@code artifact} fails.
     */
    private void processArtifact( final Artifact artifact )
        throws MojoExecutionException
    {
        if ( artifact == null )
        {
            throw new NullPointerException( "artifact" );
        }

        if ( this.isJarFile( artifact ) )
        {
            if ( this.verbose )
            {
                this.getLog().info( this.getMessage( "processing", new Object[]
                    {
                        artifact.toString()
                    } ) );

            }
            else if ( this.getLog().isDebugEnabled() )
            {
                this.getLog().debug( this.getMessage( "processing", new Object[]
                    {
                        artifact.toString()
                    } ) );

            }

            this.processArchive( artifact.getFile() );
        }
        else
        {
            if ( this.verbose )
            {
                this.getLog().info( this.getMessage( "unsupported", new Object[]
                    {
                        artifact.toString()
                    } ) );

            }
            else if ( this.getLog().isDebugEnabled() )
            {
                this.getLog().debug( this.getMessage( "unsupported", new Object[]
                    {
                        artifact.toString()
                    } ) );

            }
        }
    }

    /**
     * Processes a given archive.
     *
     * @param archive The archive to process.
     *
     * @throws NullPointerException if {@code archive} is {@code null}.
     * @throws MojoExecutionException if processing {@code archive} fails.
     */
    private void processArchive( final File archive )
        throws MojoExecutionException
    {
        if ( archive == null )
        {
            throw new NullPointerException( "archive" );
        }

        Commandline commandLine = new Commandline();

        commandLine.setExecutable( this.executable );

        commandLine.setWorkingDirectory( this.project.getBasedir() );

        if ( this.verbose )
        {
            commandLine.createArg().setValue( "-verbose" );
        }

        if ( StringUtils.isNotEmpty( maxMemory ) )
        {
            commandLine.createArg().setValue( "-J-Xmx" + maxMemory );
        }

        if ( this.arguments != null )
        {
            commandLine.addArguments( this.arguments );
        }

        commandLine = this.getCommandline( archive, commandLine );

        try
        {
            if ( this.getLog().isDebugEnabled() )
            {
                this.getLog().debug( this.getMessage( "command", new Object[]
                    {
                        this.getCommandlineInfo( commandLine )
                    } ) );

            }

            final int result = CommandLineUtils.executeCommandLine( commandLine,
                new InputStream()
            {

                public int read()
                {
                    return -1;
                }

            }, new StreamConsumer()
            {

                public void consumeLine( final String line )
                {
                    if ( verbose )
                    {
                        getLog().info( line );
                    }
                    else
                    {
                        getLog().debug( line );
                    }
                }

            }, new StreamConsumer()
            {

                public void consumeLine( final String line )
                {
                    getLog().warn( line );
                }

            } );

            if ( result != 0 )
            {
                throw new MojoExecutionException( this.getMessage( "failure", new Object[]
                    {
                        this.getCommandlineInfo( commandLine ), new Integer( result )
                    } ) );

            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( this.getMessage( "commandLineException", new Object[]
                {
                    this.getCommandlineInfo( commandLine )
                } ), e );

        }
    }

    /**
     * Locates the executable for the jarsigner tool.
     * 
     * @return The executable of the jarsigner tool, never <code>null<code>.
     */
    private String getExecutable()
    {
        String command = "jarsigner" + ( Os.isFamily( Os.FAMILY_WINDOWS ) ? ".exe" : "" );

        String executable =
            findExecutable( command, System.getProperty( "java.home" ), new String[] { "../bin", "bin", "../sh" } );

        if ( executable == null )
        {
            try
            {
                Properties env = CommandLineUtils.getSystemEnvVars();

                String[] variables = { "JDK_HOME", "JAVA_HOME" };

                for ( int i = 0; i < variables.length && executable == null; i++ )
                {
                    executable =
                        findExecutable( command, env.getProperty( variables[i] ), new String[] { "bin", "sh" } );
                }
            }
            catch ( IOException e )
            {
                if ( getLog().isDebugEnabled() )
                {
                    getLog().warn( "Failed to retrieve environment variables, cannot search for " + command, e );
                }
                else
                {
                    getLog().warn( "Failed to retrieve environment variables, cannot search for " + command );
                }
            }
        }

        if ( executable == null )
        {
            executable = command;
        }

        return executable;
    }

    /**
     * Finds the specified command in any of the given sub directories of the specified JDK/JRE home directory.
     * 
     * @param command The command to find, must not be <code>null</code>.
     * @param homeDir The home directory to search in, may be <code>null</code>.
     * @param subDirs The sub directories of the home directory to search in, must not be <code>null</code>.
     * @return The (absolute) path to the command if found, <code>null</code> otherwise.
     */
    private String findExecutable( String command, String homeDir, String[] subDirs )
    {
        if ( StringUtils.isNotEmpty( homeDir ) )
        {
            for ( int i = 0; i < subDirs.length; i++ )
            {
                File file = new File( new File( homeDir, subDirs[i] ), command );

                if ( file.isFile() )
                {
                    return file.getAbsolutePath();
                }
            }
        }

        return null;
    }

    /**
     * Gets a message for a given key from the resource bundle backing the implementation.
     *
     * @param key The key of the message to return.
     * @param args Arguments to format the message with or {@code null}.
     *
     * @return The message with key {@code key} from the resource bundle backing the implementation.
     *
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws java.util.MissingResourceException if there is no message available matching {@code key} or accessing
     * the resource bundle fails.
     */
    private String getMessage( final String key, final Object[] args )
    {
        if ( key == null )
        {
            throw new NullPointerException( "key" );
        }

        return new MessageFormat( ResourceBundle.getBundle( "jarsigner" ).getString( key ) ).format( args );
    }

}