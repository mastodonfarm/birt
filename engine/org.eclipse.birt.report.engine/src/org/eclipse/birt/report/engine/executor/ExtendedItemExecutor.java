/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.engine.executor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Level;

import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.engine.content.ContentFactory;
import org.eclipse.birt.report.engine.content.IExtendedItemContent;
import org.eclipse.birt.report.engine.content.IImageItemContent;
import org.eclipse.birt.report.engine.emitter.IReportEmitter;
import org.eclipse.birt.report.engine.emitter.IReportItemEmitter;
import org.eclipse.birt.report.engine.extension.ExtensionManager;
import org.eclipse.birt.report.engine.extension.IReportItemGeneration;
import org.eclipse.birt.report.engine.extension.IReportItemPresentation;
import org.eclipse.birt.report.engine.ir.ExtendedItemDesign;
import org.eclipse.birt.report.engine.ir.ImageItemDesign;
import org.eclipse.birt.report.engine.ir.ReportItemDesign;
import org.eclipse.birt.report.model.api.ExtendedItemHandle;

/**
 * Processes an extented item.
 */
public class ExtendedItemExecutor extends StyledItemExecutor
{

	/**
	 * @param context
	 *            the engine execution context
	 * @param visitor
	 *            visitor class used to visit the extended item
	 */
	public ExtendedItemExecutor( ExecutionContext context,
			ReportExecutorVisitor visitor )
	{
		super( context, visitor );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.executor.ReportItemExcutor#execute()
	 */
	public void execute( ReportItemDesign item, IReportEmitter emitter )
	{
		assert item instanceof ExtendedItemDesign;
		
		IExtendedItemContent content = ContentFactory.createExtendedItemContent( (ExtendedItemDesign)item );
		// handle common properties, such as
		//
		//1) style (Not supported now as we only support image extension)
		//2) highlight (Not supported now as we only support image extension)
		//3) x,y
		//4) actions
		//
		// other report item-supported features.

		//create user-defined generation-time helper object
		ExtendedItemHandle handle = (ExtendedItemHandle) item.getHandle( );
		String tagName = handle.getExtensionName( );

		IReportItemGeneration itemGeneration = ExtensionManager.getInstance( )
				.createGenerationItem( tagName );
		if ( itemGeneration == null )
		{
			// skip this element if we can not create generation-time object
			// Add Log
			return;
		}

		try
		{
			// handle the parameters passed to extension writers
			HashMap parameters = new HashMap( );
			parameters.put( IReportItemGeneration.MODEL_OBJ, handle );
			parameters.put( IReportItemGeneration.CREATED_FOR, IReportItemGeneration.CREATED_FOR_ITEM_INSTANCE );
			// TODO Add other parameters, i.e., bounds, dpi and scaling factor
			itemGeneration.initialize( parameters );
	
			itemGeneration.pushPreparedQuery( item.getQuery( ), null );
	
			// Get the dirty work done
			itemGeneration.process( context.getDataEngine( ) );
	
			// No serialization support now
			itemGeneration.serialize( null );
	
			// call getSize
			// Size size = getSize();
		}
		catch(BirtException ex)
		{
			return;
		}
		finally
		{
			// clean up
			itemGeneration.finish( );
		}

		//call the presentation peer to create the content object
		IReportItemPresentation itemPresentation = ExtensionManager
				.getInstance( ).createPresentationItem( tagName );
		if ( itemPresentation == null )
		{
			// skip this element if we can not create generation-time object
			// Add Log
			return;
		}

		try
		{
			HashMap parameters2 = new HashMap( );
			parameters2.put( IReportItemPresentation.MODEL_OBJ, handle );
			parameters2.put( IReportItemPresentation.SUPPORTED_FILE_FORMATS,
					"GIF;PNG;JPG;BMP" ); // $NON-NLS-1$
			// TODO Add other parameters, i.e., bounds, dpi and scaling factor
			itemPresentation.initialize( parameters2 );
	
			// No de-serialization support for now
			itemPresentation.restore( null );
	
			// Do the dirty work
			Object output = itemPresentation.process( );
			
			if (output != null)
			{
				//output the content created by IReportItemPresentation
				String format = emitter.getOutputFormat( );
				String mimeType = ""; // $NON-NLS-1$
				int type = itemPresentation.getOutputType( format, mimeType );
				handleItemContent(item, emitter, content, type, output);
			}
		}
		catch(BirtException ex)
		{
		}
		finally
		{
			itemPresentation.finish( );
		}
	}
	
	
	/**
	 * handle the content created by the IPresentation
	 * @param item extended item design
	 * @param emitter emitter used to output the contnet
	 * @param content ext content
	 * @param type output type
	 * @param output output
	 */
	protected void handleItemContent(ReportItemDesign item, IReportEmitter emitter, IExtendedItemContent content,
			int type, Object output)
	{
		switch ( type )
		{
			case IReportItemPresentation.OUTPUT_NONE :
				break;
			case IReportItemPresentation.OUTPUT_AS_IMAGE :
				// the output object is a image, so create a image content
				// object
				IImageItemContent image = ContentFactory.createImageContent( item );
				if (output instanceof InputStream)
				{
					image.setData(readContent((InputStream)output));
				}
				else if (output instanceof byte[])
				{
					image.setData( (byte[]) output );
				}
				else
				{
					assert false;
					logger.log( Level.SEVERE,"unsupport image type:{0}", output);

				}
				image.setImageSource( ImageItemDesign.IMAGE_EXPRESSION );
				IReportItemEmitter imageEmitter = emitter.getEmitter( "image" ); // $NON-NLS-1$
				if ( imageEmitter != null )
				{
					imageEmitter.start( image );
					imageEmitter.end( );
				}
				break;
			case IReportItemPresentation.OUTPUT_AS_CUSTOM :
				content.setContent( output );
				//get the emmiter type, and give it to others type
				IReportItemEmitter itemEmitter = emitter
						.getEmitter( "extendedItem" ); // $NON-NLS-1$
				if ( itemEmitter != null )
				{
					itemEmitter.start( content );
					itemEmitter.end( );
				}
				break;
			case IReportItemPresentation.OUTPUT_AS_DRAWING :
			case IReportItemPresentation.OUTPUT_AS_HTML_TEXT :
			case IReportItemPresentation.OUTPUT_AS_TEXT :
				assert false;
				break;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.engine.executor.ReportItemExecutor#reset()
	 */
	public void reset( )
	{
		// TODO Auto-generated method stub

	}
	
	/**
	 * read the content of input stream.
	 * @param in input content
	 * @return content in the stream.
	 */
	static protected byte[] readContent(InputStream in)
	{
		BufferedInputStream bin = in instanceof BufferedInputStream ? (BufferedInputStream)in : new BufferedInputStream(in);
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		byte[] buffer = new byte[1024];
		int readSize = 0;
		try
		{
			readSize = bin.read(buffer);
			while (readSize != -1)
			{
				out.write(buffer, 0, readSize);
				readSize = bin.read(buffer);
			}
		}
		catch(IOException ex)
		{
		    logger.log( Level.SEVERE, ex.getMessage(), ex);
		}
		return out.toByteArray();
	}

}