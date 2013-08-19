package com.uionsys.dmdocfetcher;

import javax.ejb.Remote;

@Remote
public interface DocFetcherRemote
{
	public String[] GetDocumentNameAndType(final String DOCNUMBER);
	
	public byte[] FetchDocument(String DOCNUMBER);
}
