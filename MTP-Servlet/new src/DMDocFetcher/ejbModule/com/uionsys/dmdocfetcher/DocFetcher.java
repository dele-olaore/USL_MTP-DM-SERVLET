package com.uionsys.dmdocfetcher;

import java.io.ByteArrayOutputStream;

import com.dexter.util.winreg.WinRegistry;
import com.jacob.activeX.*;
import com.jacob.com.*;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;

/**
 * Session Bean implementation class DocFetcher.
 * Provides remote function to fetch document binary content from DM.
 */
@Stateless
@LocalBean
public class DocFetcher implements DocFetcherRemote
{
	// varables for authentication and DMS access
	private String dst, username, password, library, group, webstr;
	
	// error detection varables
	private int errNumber;
	private String errDesc;
	
    /**
     * Default constructor. 
     */
    public DocFetcher()
    {
    	init(); // init the system to load settings
    }
    
    private void init()
    {
    	setLibrary("DM_LIB");
		setUsername("Administrator");
		setPassword("mgs");
		setWebstr("http://192.168.1.77/cyberdocs");
		
		loadProperties();
    }
    
    /**
	 * Loads the properties from windows registry.
	 * */
	private void loadProperties()
	{
		String value = "";
		
		String sKey = "Software\\TIPlus_DMInterface\\DMParameters";
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "UserId");
			if(value != null)
				setUsername(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "Password");
			if(value != null)
				setPassword(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
		try
		{
			value = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER, sKey, "Library");
			if(value != null)
				setLibrary(value);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
    
	/**
	 * Used to login to the eDocs DMS. Sets the DST for further usage by other methods.
	 * */
	private void login()
	{
		ComThread.InitSTA();
		
		try
		{
			// PDCLogin object
			ActiveXComponent pcdLogin = new ActiveXComponent("PCDClient.PCDLogin");
			
			if(pcdLogin != null)
			{
				pcdLogin.invoke("AddLogin", new Variant[] {new Variant(0), new Variant(getLibrary()), new Variant(getUsername()), new Variant(getPassword())});
				
				Variant error = pcdLogin.getProperty("ErrNumber");
				
				if(error.getInt() == 0) // no errors so continue
				{
					pcdLogin.invoke("Execute");
					error = pcdLogin.getProperty("ErrNumber");
					if(error.getInt() == 0) // no error login successful
					{
						Variant v = pcdLogin.invoke("GetLoginLibrary");
						setLibrary(v.getString());
						
						v = pcdLogin.invoke("GetDOCSUserName");
						setUsername(v.getString());
						
						v = pcdLogin.invoke("GetPrimaryGroup");
						setGroup(v.getString());
						
						v = pcdLogin.invoke("GetDST"); // get DST
						setDst(v.getString()); // set DST
					}
					else // show error message
					{
						setErrNumber(error.getInt());
						Variant errdesc = pcdLogin.getProperty("ErrDescription");
						setErrDesc("Error: " + errdesc.getString());
					}
				}
				else
				{
					setErrNumber(error.getInt());
					Variant errdesc = pcdLogin.getProperty("ErrDescription");
					setErrDesc("Error: " + errdesc.getString());
				}
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			setErrDesc("Error: " + ex.getMessage());
		}
		finally
		{
			ComThread.Release();
		}
	}
	
	/**
	 * Fetch a document application type and name.
	 * @return A string array containing the document name at index 0 and the application type at index 1, or null if not found.
	 * @author oladele olaore
	 * */
	public String[] GetDocumentNameAndType(final String DOCNUMBER)
	{
		String[] ret = null;
		
		if(getDst() == null) // not logged in, so log in
			login();
		
		if(getDst() == null) // after login, if its still null, then return
    		return null;
    	
		ComThread.InitSTA(); // init the dll calling thread
		
		try
		{
			ActiveXComponent pcdSearchDocsObj = new ActiveXComponent("PCDClient.PCDSearch");

			if(pcdSearchDocsObj != null)
			{
				pcdSearchDocsObj.invoke("SetDST", new Variant[] {new Variant(getDst())});
				pcdSearchDocsObj.invoke("AddSearchLib", new Variant[] {new Variant(getLibrary())});

				pcdSearchDocsObj.invoke("SetSearchObject", new Variant[] {new Variant("CYD_DEFPROF")}); // default profile so as to get the details we need.
				System.out.println("DOCNUMBER: " + DOCNUMBER);
				if(DOCNUMBER != null && DOCNUMBER.trim().length() > 0)
					pcdSearchDocsObj.invoke("AddSearchCriteria", new Variant[] {new Variant("DOCNUM"), new Variant(DOCNUMBER)});
				
				pcdSearchDocsObj.invoke("AddReturnProperty", new Variant[] {new Variant("DOCNUM")});
				pcdSearchDocsObj.invoke("AddReturnProperty", new Variant[] {new Variant("APP_ID")});
				pcdSearchDocsObj.invoke("AddReturnProperty", new Variant[] {new Variant("DOCNAME")});
				
				pcdSearchDocsObj.invoke("Execute");

				Variant error = pcdSearchDocsObj.getProperty("ErrNumber");

				if(error.getInt() == 0) // no errors so continue
				{
					int rowCount = 0;
					System.out.println("Rows found: " + rowCount);
					Variant v = pcdSearchDocsObj.invoke("GetRowsFound");
					rowCount = v.getInt();

					for(int i=0; i<rowCount; i++)
					{
						pcdSearchDocsObj.invoke("NextRow");
						error = pcdSearchDocsObj.getProperty("ErrNumber");
						if(error.getInt() == 0)
						{
							ret = new String[2];
							
							Variant v2 = pcdSearchDocsObj.invoke("GetPropertyValue", new Variant[] {new Variant("DOCNUM")});
							
							v2 = pcdSearchDocsObj.invoke("GetPropertyValue", new Variant[] {new Variant("DOCNAME")});
							ret[0] = v2.getString();
							
							v2 = pcdSearchDocsObj.invoke("GetPropertyValue", new Variant[] {new Variant("APP_ID")});
							ret[1] = v2.getString();
						}
						else
						{
							Variant errdesc = pcdSearchDocsObj.getProperty("ErrDescription");
							System.out.println("Error: " + errdesc.getString());
						}
					}

					pcdSearchDocsObj.invoke("ReleaseResults");
				}
				else
				{
					Variant errdesc = pcdSearchDocsObj.getProperty("ErrDescription");
					System.out.println("Error: " + errdesc.getString());
				}
			}
			else
			{
				System.out.println("PCDClient.PCDSearch not found!");
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			setErrDesc(ex.getMessage());
		}
		finally
		{
			ComThread.Release();
		}
		
		return ret;
	}
	
    /**
     * Fetch a document from DM and returns the byte array if found, else returns null.
     * @return Document byte array or null if not found.
     * @author Oladele Olaore
     * */
    public byte[] FetchDocument(final String DOCNUMBER)
    {
    	byte[] bytes = null;
    	
    	if(getDst() == null) // not logged in, so log in
			login();
    	
    	if(getDst() == null) // after login, if its still null, then return
    		return bytes;
    	
		ComThread.InitSTA(); // init the dll calling thread
		
		String VERSION_ID = null;
		
		// First we need to get the version id of the first version of the document.
		
		try
		{
			// SQL object to locate version id
			ActiveXComponent pcdSQLObj = new ActiveXComponent("PCDClient.PCDSQL");
			
			if(pcdSQLObj != null)
			{
				pcdSQLObj.invoke("SetDST", new Variant[] {new Variant(getDst())});
				pcdSQLObj.invoke("SetLibrary", new Variant[] {new Variant(getLibrary())});
				
				// get the latest version of the document
				pcdSQLObj.invoke("Execute", new Variant[] {new Variant("SELECT max(VERSION_ID) from DOCSADM.VERSIONS where DOCNUMBER = " + DOCNUMBER)});
				
				Variant error = pcdSQLObj.getProperty("ErrNumber");
				
				if(error.getInt() == 0) // no errors so continue
				{
					int rowCount = 0;
					Variant v = pcdSQLObj.invoke("GetRowCount");
					rowCount = v.getInt();
					System.out.println("ROW COUNT: " + rowCount);
					error = pcdSQLObj.getProperty("ErrNumber");
					
					if(error.getInt() == 0) // no errors so continue
					{
						for(int i=0; i<rowCount; i++)
						{
							pcdSQLObj.invoke("SetRow", new Variant[] {new Variant(i+1)});
							v = pcdSQLObj.invoke("GetColumnValue", new Variant[] {new Variant(1)});
							
							error = pcdSQLObj.getProperty("ErrNumber");
							
							if(error.getInt() == 0) // no errors so continue
							{
								System.out.println("Getting version id");
								VERSION_ID = "" + v.getString(); // version id
								//break;
							}
							else
							{
								Variant errdesc = pcdSQLObj.getProperty("ErrDescription");
								setErrDesc("Error: " + errdesc.getString());
							}
						}
					}
					else
					{
						Variant errdesc = pcdSQLObj.getProperty("ErrDescription");
						setErrDesc("Error: " + errdesc.getString());
					}
					
					pcdSQLObj.invoke("ReleaseResults");
				}
				else
				{
					Variant errdesc = pcdSQLObj.getProperty("ErrDescription");
					setErrDesc("Error: " + errdesc.getString());
				}
			}
			else
			{
				setErrDesc("PCDClient.PCDSQL not found!");
			}
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			setErrDesc(ex.getMessage());
		}
		
		// found
		if(VERSION_ID != null)
		{
			System.out.println("VERSION ID: " + VERSION_ID);
			System.out.println("DOCNUMBER: " + DOCNUMBER);
			if(VERSION_ID.trim().length() == 0)
			{
				VERSION_ID = "1";
				System.out.println("Defaulting id to 1");
			}
			try
			{
				// get document
				ActiveXComponent pcdGetDocsObj = new ActiveXComponent("PCDClient.PCDGetDoc");
				
				if(pcdGetDocsObj != null)
				{
					pcdGetDocsObj.invoke("SetDST", new Variant[] {new Variant(dst)});
					pcdGetDocsObj.invoke("AddSearchCriteria", new Variant[] {new Variant("%TARGET_LIBRARY"), new Variant(getLibrary())});
					pcdGetDocsObj.invoke("AddSearchCriteria", new Variant[] {new Variant("%DOCUMENT_NUMBER"), new Variant(DOCNUMBER)});
					pcdGetDocsObj.invoke("AddSearchCriteria", new Variant[] {new Variant("%VERSION_ID"), new Variant(VERSION_ID)});
					
					pcdGetDocsObj.invoke("Execute");
					
					Variant error = pcdGetDocsObj.getProperty("ErrNumber");
					
					if(error.getInt() == 0) // no errors so continue
					{
						int rowCount = 0;
						Variant v = pcdGetDocsObj.invoke("GetRowsFound");
						rowCount = v.getInt();
						
						error = pcdGetDocsObj.getProperty("ErrNumber");
						
						if(error.getInt() == 0) // no errors so continue
						{
							for(int i=0; i<rowCount; i++)
							{
								pcdGetDocsObj.invoke("NextRow");
								error = pcdGetDocsObj.getProperty("ErrNumber");
								if(error.getInt() == 0)
								{
									if(i == 0) // first object in record is the doc, so get the content.
									{
										Variant doc = pcdGetDocsObj.invoke("GetPropertyValue", new Variant[] {new Variant("%CONTENT")});
										if(doc != null)
										{
											try
											{
												// empty output stream to write out the contents to from eDocs
												ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
												
												// read based on a buffer size or 1024bytes, apx 1kb
												Variant vbytes = Dispatch.call(doc.getDispatch(), "Read", new Object[] {"1024"});
												SafeArray sarr = vbytes.toSafeArray();
												
												// write to the empty stream
												byteOut.write(sarr.toByteArray());
												
												Variant bytesRead = Dispatch.call(doc.getDispatch(), "BytesRead");
												while(bytesRead.getInt() > 0) // read until no more
												{
													vbytes = Dispatch.call(doc.getDispatch(), "Read", new Object[] {"1024"});
													sarr = vbytes.toSafeArray();
													
													// write to the empty stream
													byteOut.write(sarr.toByteArray());
													
													bytesRead = Dispatch.call(doc.getDispatch(), "BytesRead"); // continue read
												}
												
												// assign our return varable from the bytes in the output stream.
												bytes = byteOut.toByteArray();
												byteOut.close(); // close stream
											}
											catch(Exception ex)
											{
												ex.printStackTrace();
												setErrDesc(ex.getMessage());
											}
										}
										else
										{
											setErrDesc("Error: Document is null");
										}
									}
								}
							}
						}
						else
						{
							Variant errdesc = pcdGetDocsObj.getProperty("ErrDescription");
							setErrDesc("Error: " + errdesc.getString());
						}
					}
					else
					{
						Variant errdesc = pcdGetDocsObj.getProperty("ErrDescription");
						setErrDesc("Error: " + errdesc.getString());
					}
				}
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				setErrDesc(ex.getMessage());
			}
			finally
			{
				ComThread.Release();
			}
		}
    	
    	return bytes;
    }

	public String getDst() {
		return dst;
	}

	public void setDst(String dst) {
		this.dst = dst;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getLibrary() {
		return library;
	}

	public void setLibrary(String library) {
		this.library = library;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getWebstr() {
		return webstr;
	}

	public void setWebstr(String webstr) {
		this.webstr = webstr;
	}

	public int getErrNumber() {
		return errNumber;
	}

	public void setErrNumber(int errNumber) {
		this.errNumber = errNumber;
	}

	public String getErrDesc() {
		return errDesc;
	}

	public void setErrDesc(String errDesc) {
		this.errDesc = errDesc;
		System.out.println("Err: " + errDesc);
	}
    
}
