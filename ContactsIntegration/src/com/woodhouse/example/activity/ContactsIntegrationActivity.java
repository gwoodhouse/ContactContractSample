/*
 * Copyright (C) 2012 Graeme Woodhouse
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

package com.woodhouse.example.activity;

import java.util.ArrayList;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.woodhouse.example.R;

public class ContactsIntegrationActivity extends Activity {
	
    // Request code for the contact picker activity
    private static final int PICK_CONTACT_REQUEST = 1;
    
    private TextView mResult;
    private String mRawContactId;
    
    private String mAccountName;
    private String mAccountType;
 
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mResult = (TextView) findViewById(R.id.text);
        
        startActivityForResult(new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI), PICK_CONTACT_REQUEST);
        
    }    

    /**
     * Invoked when the contact picker activity is finished. The {@code contactUri} parameter
     * will contain a reference to the contact selected by the user. We will treat it as
     * an opaque URI and allow the SDK-specific ContactAccessor to handle the URI accordingly.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            loadContactInfo(data.getData());
        }
    }
    
    /**
     * Load contact information on a background thread.
     */
    private void loadContactInfo(Uri contactUri) {

        /*
         * We should always run database queries on a background thread. The database may be
         * locked by some process for a long time.  If we locked up the UI thread while waiting
         * for the query to come back, we might get an "Application Not Responding" dialog.
         */
        AsyncTask<Uri, Void, Boolean> task = new AsyncTask<Uri, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Uri... uris) {              
                Log.v("Retreived ContactURI", uris[0].toString());
                
                mRawContactId = getRawContactContainingHomeEmail(uris[0]);

                return mRawContactId != null;
            }

            @Override
            protected void onPostExecute(Boolean exists) {
            	if(exists)  {
            		Log.v("", "Updating...");
            		updateContact();
            	}
            	else {
            		Log.v("", "Inserting...");
            		insertEmailContact();
            	}
            }
        };

        task.execute(contactUri);
    }
    
    private String getRawContactContainingHomeEmail(Uri contactUri) {
    	String mRawContactIdOfHomeEmail = null;
    	
    	Cursor mContactCursor = getContentResolver().query(contactUri, null, null, null, null);
    	Log.v("Contact", "Got Contact Cursor");

        try {
            if (mContactCursor.moveToFirst()) {
            	String mContactId = getCursorString(mContactCursor, 
            			ContactsContract.Contacts._ID);
            	
    			Cursor mRawContactCursor = getContentResolver().query(
    					RawContacts.CONTENT_URI, 
    					null, 
    					Data.CONTACT_ID + " = ?",
    					new String[] {mContactId}, 
    					null);
            	
    			Log.v("RawContact", "Got RawContact Cursor");
    			
            	try {
            		ArrayList<String> mRawContactIds = new ArrayList<String>();
            		while(mRawContactCursor.moveToNext()) {
            			mAccountName = getCursorString(mRawContactCursor, RawContacts.ACCOUNT_NAME);
            			mAccountType = getCursorString(mRawContactCursor, RawContacts.ACCOUNT_TYPE);
            			Log.v("RawContact", "AccountName: " + mAccountName + "AccountType" + mAccountType);
            			String rawId = getCursorString(mRawContactCursor, RawContacts._ID);
            			Log.v("RawContact", "ID: " + rawId);
            			mRawContactIds.add(rawId);
            		}
            		
            		for(String rawId : mRawContactIds) {            			
            			Cursor mDataCursor = getContentResolver().query(
            					Data.CONTENT_URI,
            					null,
            					Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + Email.TYPE + " = ?",
            					new String[] { rawId, Email.CONTENT_ITEM_TYPE, String.valueOf(Email.TYPE_HOME)},
            					null);
            			            			
            			if(mDataCursor.getCount() > 0) {
            				Log.v("Data", "Found data item with MIMETYPE and EMAIL.TYPE");
            				mRawContactIdOfHomeEmail = rawId;
            				mDataCursor.close();
            				break;
            			} else {
            				Log.v("Data", "Data doesn't contain MIMETYPE and EMAIL.TYPE");
            				mDataCursor.close();
            			}            			
            		}			        		
            	} finally {
            		mRawContactCursor.close();
            	}
            }
        } catch(Exception e) {
        	Log.w("UpdateContact", e.getMessage());
        	for(StackTraceElement ste : e.getStackTrace()) {
        		Log.w("UpdateContact", "\t" + ste.toString());
        	}
        } finally {			    
            mContactCursor.close();			        
        }
        
        return mRawContactIdOfHomeEmail;
    }
    
    private static String getCursorString(Cursor cursor, String columnName) {
    	int index = cursor.getColumnIndex(columnName);
    	if(index != -1) return cursor.getString(index);
    	return null;
    }
    
    public void insertEmailContact() {
        try {
			ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
			
			ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
			          .withValue(RawContacts.ACCOUNT_TYPE, mAccountType)
			          .withValue(RawContacts.ACCOUNT_NAME, mAccountName)
			          .build());
			
			ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
					.withValueBackReference(Data.RAW_CONTACT_ID, 0)
					.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
					.withValue(Data.DATA1, "somebody@android.com")
					.withValue(Email.TYPE, Email.TYPE_HOME)
					.withValue(Email.DISPLAY_NAME, "Email")
					.build());
			getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
			mResult.setText("inserted");
       } catch (Exception e) {
           // Display warning
	       	Log.w("UpdateContact", e.getMessage());
	    	for(StackTraceElement ste : e.getStackTrace()) {
	    		Log.w("UpdateContact", "\t" + ste.toString());
	    	}
           Context ctx = getApplicationContext();
           int duration = Toast.LENGTH_SHORT;
           Toast toast = Toast.makeText(ctx, "Update failed", duration);
           e.printStackTrace();
           toast.show();
       }
    }  
    
    
    public void updateContact() {
        try {
			ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
			
			ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
				.withSelection(Data.RAW_CONTACT_ID + " = ?", new String[] {mRawContactId})
				.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
				.withValue(Data.DATA1, "somebody@android.com")
				.withValue(Email.TYPE, Email.TYPE_HOME)
				.build());
			getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		    mResult.setText("Updated");
        } catch (Exception e) {
            // Display warning
        	Log.w("UpdateContact", e.getMessage());
        	for(StackTraceElement ste : e.getStackTrace()) {
        		Log.w("UpdateContact", "\t" + ste.toString());
        	}
            Context ctx = getApplicationContext();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(ctx, "Update failed", duration);
            toast.show();
        }
    }       
}