package com.uionsys.dmdocfetcher.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dexter.util.winreg.WinRegistry;
import com.uionsys.dmdocfetcher.DocFetcherRemote;

/**
 * Servlet implementation class DocFetcher
 */
@WebServlet(description = "Document fetcher servlet", urlPatterns = { "/DocFetcher" })
public class DocFetcher extends HttpServlet
{
	private static final long serialVersionUID = 1L;
	
	private Context context;
	private DocFetcherRemote docFetcherRemote;
	
	private String factoryContext = "org.jboss.as.naming.InitialContextFactory";
	private String url_prefixes = "org.jboss.naming:org.jnp.interfaces";
	private String url_provider = "jnp://127.0.0.1:9999";
	
	private String beanLookupName = "java:global/DMDocFetcher/DocFetcher!com.uionsys.dmdocfetcher.DocFetcherRemote";
	
    /**
     * @see HttpServlet#HttpServlet()
     */
    public DocFetcher()
    {
        super();
        
        try
        {
        	// load values from registry
        	loadProperties();
	        
	        Properties p = new Properties();
	        p.put(Context.INITIAL_CONTEXT_FACTORY, getFactoryContext());
	        p.put(Context.URL_PKG_PREFIXES, getUrl_prefixes());
	        p.put(Context.PROVIDER_URL, getUrl_provider());
	        
	        context = new InitialContext(p);
        }
        catch(Exception ex)
        {}
    }
    
    /**
	 * Loads the properties from windows registry.
	 * */
	private void loadProperties()
	{
		String value = "";
		
		String sKey = "Software\\Union_DocFetcher\\DOCParameters";
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "CONTEXT_FACTORY");
			if(value != null)
				setFactoryContext(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "URL_PREFIXES");
			if(value != null)
				setUrl_prefixes(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "URL_PROVIDER");
			if(value != null)
				setUrl_provider(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "BEAN_LOOKUP_NAME");
			if(value != null)
				setBeanLookupName(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		ServletContext context = getServletConfig().getServletContext();
		
		// supplied from the request
		String DOCNUMBER = request.getParameter("DOCNUMBER");
		System.out.println("DOCNUMBER: " + DOCNUMBER);
		
		// get the document bytes
		byte[] bytes = getDocBytes(DOCNUMBER);
		
		// available
		if(bytes != null)
		{
			// get the name and type of document
			String[] details = getDocDetails(DOCNUMBER);
			
			// default name and type
			String mimetype = "application/octet-stream";
			String FILENAME = "DOCUMENT";
			
			if(details != null && details[0] != null && details[1] != null)
			{
				FILENAME = details[0];
				if(details[1].toLowerCase().indexOf("word") >= 0)
				{
					FILENAME = FILENAME + ".doc";
				}
				else if(details[1].toLowerCase().indexOf("acrobat") >= 0)
				{
					FILENAME = FILENAME + ".pdf";
				}
				else if(details[1].toLowerCase().indexOf("notepad") >= 0)
				{
					FILENAME = FILENAME + ".txt";
				}
				else if(details[1].toLowerCase().indexOf("excel") >= 0)
				{
					FILENAME = FILENAME + ".xsl";
				}
				else if(details[1].toLowerCase().indexOf("jpg") >= 0)
				{
					FILENAME = FILENAME + ".jpg";
				}
				else if(details[1].toLowerCase().indexOf("gif") >= 0)
				{
					FILENAME = FILENAME + ".gif";
				}
				else if(details[1].toLowerCase().indexOf("png") >= 0)
				{
					FILENAME = FILENAME + ".png";
				}
				
				mimetype = context.getMimeType(FILENAME);
			}
			
			// bytes array for the document bytes
			ByteArrayInputStream bytesReader = null;
			
			try
			{
				bytesReader = new ByteArrayInputStream(bytes);
				
				// set our response headers
				response.setContentType((mimetype != null) ? mimetype : "application/octet-stream");
				response.setContentLength(bytes.length);
				response.setHeader("Content-Disposition", "attachment; filename=\"" + FILENAME + "\"");
				
				ServletOutputStream op = response.getOutputStream();
				
				// write out the bytes until its finished from the reader
				try
				{
					int anInt = 0;
					while((anInt=bytesReader.read())!=-1)
						op.write(anInt);
				}
				catch(IOException ioe)
				{
					ioe.printStackTrace();
				}
				finally
				{
					bytesReader.close();
				}
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		else // not found, just a simple file not found message
		{
			String html = "<html><body><h2 align=center><font color=red>File not found</font></h2></body></html>";
			
			ServletOutputStream op = response.getOutputStream();
			try
			{
				op.write(html.getBytes());
				op.flush();
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		// just call doGet
		doGet(request, response);
	}

	private String[] getDocDetails(String DOCNUMBER)
	{
		String[] ret = null;
		
		try
        {
			loadProperties();
	        
	        Properties p = new Properties();
	        p.put(Context.INITIAL_CONTEXT_FACTORY, getFactoryContext()); // "org.jboss.as.naming.InitialContextFactory"
	        p.put(Context.URL_PKG_PREFIXES, getUrl_prefixes()); // "org.jboss.naming:org.jnp.interfaces"
	        p.put(Context.PROVIDER_URL, getUrl_provider()); // "jnp://127.0.0.1:9999"
	        
	        context = new InitialContext(p);
	        
	        Object bean = context.lookup(getBeanLookupName());
	        docFetcherRemote = (DocFetcherRemote) bean;
	        
	        System.out.println("Object retrieved");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		
		if(docFetcherRemote != null)
		{
			try
			{
				ret = docFetcherRemote.GetDocumentNameAndType(DOCNUMBER);
				System.out.println("Object method called");
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		
		return ret;
	}
	
	private byte[] getDocBytes(String DOCNUMBER)
	{
		byte[] bytes = null;
		
		try
        {
			loadProperties();
	        
	        Properties p = new Properties();
	        p.put(Context.INITIAL_CONTEXT_FACTORY, getFactoryContext()); // "org.jboss.as.naming.InitialContextFactory"
	        p.put(Context.URL_PKG_PREFIXES, getUrl_prefixes()); // "org.jboss.naming:org.jnp.interfaces"
	        p.put(Context.PROVIDER_URL, getUrl_provider()); // "jnp://127.0.0.1:9999"
	        
	        context = new InitialContext(p);
	        
	        Object bean = context.lookup(getBeanLookupName());
	        docFetcherRemote = (DocFetcherRemote) bean;
	        
	        System.out.println("Object retrieved");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		
		if(docFetcherRemote != null)
		{
			try
			{
				bytes = docFetcherRemote.FetchDocument(DOCNUMBER);
				System.out.println("Object method called");
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
			}
		}
		
		return bytes;
	}

	public String getFactoryContext() {
		return factoryContext;
	}

	public void setFactoryContext(String factoryContext) {
		this.factoryContext = factoryContext;
	}

	public String getUrl_prefixes() {
		return url_prefixes;
	}

	public void setUrl_prefixes(String url_prefixes) {
		this.url_prefixes = url_prefixes;
	}

	public String getUrl_provider() {
		return url_provider;
	}

	public void setUrl_provider(String url_provider) {
		this.url_provider = url_provider;
	}

	public String getBeanLookupName() {
		return beanLookupName;
	}

	public void setBeanLookupName(String beanLookupName) {
		this.beanLookupName = beanLookupName;
	}
	
}
