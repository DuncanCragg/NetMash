
package android;

import java.util.*;
import java.util.regex.*;

import android.gui.*;
import android.os.*;

import android.content.*;
import android.database.Cursor;
import android.location.*;
import android.accounts.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.*;

import static android.content.Context.*;
import static android.location.LocationManager.*;

import netmash.lib.JSON;
import netmash.forest.*;
import netmash.platform.Kernel;

/** Access to User's contacts list.
*/
public class UserContacts {

    static String getUsersFullName(){
        AccountManager acctmgr = AccountManager.get(NetMash.top);
        Account[] accounts = acctmgr.getAccountsByType("com.google");
        for(Account account: accounts){
            String name=account.name;
            String[] parts=name.split("@");
            if(parts.length!=0) return toFullNameFromEmail(parts[0]);
        }
        return "You";
    }

    static public String toFullNameFromEmail(String emailname){
        String[] parts=emailname.split("[ \\.\\-_]");
        StringBuilder sb=new StringBuilder();
        for(String part: parts){
            if(part.length()==0) continue;
            char[] partcha = part.toCharArray();
            partcha[0]=Character.toUpperCase(partcha[0]);
            sb.append(partcha);
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    static public LinkedList populateContacts(User user){ WebObject.logrule();
        LinkedList contactslist = new LinkedList();
        if(NetMash.top==null) return null;
        Context context = NetMash.top.getApplicationContext();
        ContentResolver cr=context.getContentResolver();
        Cursor concur = cr.query(Contacts.CONTENT_URI, null, null, null, null);
        int idcol   = concur.getColumnIndex(Contacts._ID);
        int namecol = concur.getColumnIndex(Contacts.DISPLAY_NAME);
        int hasphone= concur.getColumnIndex(Contacts.HAS_PHONE_NUMBER);
int i=0;
        if(concur.moveToFirst()) do{
            String id   = concur.getString(idcol);
            String name = concur.getString(namecol);
            if(name==null) continue;
            String phonenumber=null;
            if(Integer.parseInt(concur.getString(hasphone)) >0){
                   phonenumber =getPhoneNumber(cr, id);
            }
            String emailaddress=getEmailAddress(cr, id);
            String address     =getAddress(cr, id);
            if(phonenumber==null && emailaddress==null && address==null) continue;
            WebObject.log("Contact: "+id+" "+name+" "+phonenumber+" "+emailaddress+" "+address);
            contactslist.add(createVCard(user, id, name, phonenumber, emailaddress, address));
if(++i>100) break;
        } while(concur.moveToNext());
        concur.close();
        return contactslist;
    }

    static public String getPhoneNumber(ContentResolver cr, String id){
        Cursor phonecur=cr.query(Phone.CONTENT_URI, null, Phone.CONTACT_ID+" = ?", new String[]{id}, null);
        String phonenumber=null;
        while(phonecur.moveToNext()) {
            phonenumber = phonecur.getString(phonecur.getColumnIndex(Phone.NUMBER));
            break;
        }
        phonecur.close();
        return phonenumber;
    }

    static public String getEmailAddress(ContentResolver cr, String id){
        Cursor emailcur = cr.query(Email.CONTENT_URI, null, Email.CONTACT_ID+" = ?", new String[]{id}, null);
        String emailaddress=null;
        while(emailcur.moveToNext()) {
            String emailType = emailcur.getString(emailcur.getColumnIndex(Email.TYPE));
            emailaddress     = emailcur.getString(emailcur.getColumnIndex(Email.DATA));
            break;
        }
        emailcur.close();
        return emailaddress;
    }

    static public String getAddress(ContentResolver cr, String id){
        Cursor addcur = cr.query(StructuredPostal.CONTENT_URI, null, StructuredPostal.CONTACT_ID+" = ?", new String[]{id}, null);
        String address=null;
        while(addcur.moveToNext()){
            address = addcur.getString(addcur.getColumnIndex(StructuredPostal.FORMATTED_ADDRESS));
            break;
        }
        addcur.close();
        return address;
    }

    static public String createVCard(User user, String id, String name, String phonenumber, String emailaddress, String address){
        String inlineaddress = address!=null? address.replaceAll("\n", ", "): "";
        return user.spawn(newVcard(name, phonenumber, emailaddress, inlineaddress));
    }

    static User newVcard(String name, String phonenumber, String emailaddress, String address){
        return new User(         "{ \"is\": \"vcard\", \n"+
                                 "  \"fullName\": \""+name+"\""+
            (phonenumber!=null?  ",\n  \"tel\": \""+phonenumber+"\"": "")+
            (emailaddress!=null? ",\n  \"email\": \""+emailaddress+"\"": "")+
            (address!=null?      ",\n  \"address\": \""+address+"\"": "")+
                                 "\n}");
    }
}

