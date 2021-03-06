package controllers;

import java.net.URLEncoder;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.*;
 
import play.*;
import play.mvc.*;
import play.libs.Crypto;
import play.libs.Mail;
import play.data.validation.*;
import java.io.UnsupportedEncodingException;

import notifiers.*;
import models.*;
import play.*;
import play.i18n.Messages;

import javax.naming.NamingEnumeration;
import javax.naming.directory.*;

/**
 * Invitation Controller
 * @method: inviteNewMember
 *
 * Other methods useless ...
 */

@With(Secure.class)
public class Invitation extends BaseController {
        public static int USER_NOTEXIST =1;
        public static int ADDRESSES_MATCHE =2;
        public static int ADDRESSES_NOTMATCHE = 3;

	@Before
	static void saveValuesIntoSession()
	{   
		session.put("nom", params.get("nom"));
		session.put("prenom", params.get("prenom"));
		session.put("mail", params.get("mail"));
		session.put("langue", params.get("langue"));
		System.out.println("request.url: "+request.url);
		System.out.println("request.method: "+request.method);
		session.put("url",request.url);
		session.put("method",request.method);
	}

	public static void index() {
	render();
	}
	  public static void invitation(){

		params.put("nom", session.get("nom") );
		params.put("prenom", session.get("prenom") );
		params.put("mail", session.get("mail") );
		params.put("login", session.get("login") );
        
		String lang = session.get("langue");
		if(lang != null) {
			params.put("checked_fr", "");
			params.put("checked_en", "");

			if(lang.equals("fr"))
			{
				params.put("checked_fr", "checked");
				System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
			}
			else if(lang.equals("en"))
			{
				params.put("checked_en", "checked");
			}
			else
			{
				System.out.println("Language error! Please check controller Invitation");
			}
		}                    
		render("Invitation/index.html");
	}
	public static void inviteNewMember(@Required String nom,@Required String prenom, @Required String mail, @Required String langue) {
		 
		try {
            String login = normalize(prenom)+'.'+normalize(nom);
            String url = "";
            String signature = "";
            String community = "Hypertopic";
            //
            String mailGodfather="";
            String firstNameGodfather="";
            String lastNameGodfather="";
            int flag=-1;


            if(session.get("username").equals("admin")){
            	firstNameGodfather="l'administrateur";
            	mailGodfather="Hypertopic Team <noreply@hypertopic.org>";
            }
            else{
            HashMap<String, String> infos = Ldap.getConnectedUserInfos(session.get("username"));
            mailGodfather=infos.get("mail");
            firstNameGodfather=infos.get("firstName");
            lastNameGodfather=infos.get("lastName");
                    }
            flag = Invitation.verifyMaliciousPassword(login, mail);
            if(flag == Invitation.ADDRESSES_MATCHE || flag ==Invitation.USER_NOTEXIST){

            System.out.println("invitenewmember");
            try {
                url = "http://" + request.domain;
                if (request.port!=80) url += ":" + request.port;
                url += "/inscription?firstname=" + URLEncoder.encode(prenom, "UTF-8") + "&lastname=" + URLEncoder.encode(nom, "UTF-8") + "&email=" + URLEncoder.encode(mail, "UTF-8");
                signature = Crypto.sign(prenom + nom + mail);
                url += "&signature=" + signature;
                System.out.println("url in inviteNewMember: "+url);
            } catch (UnsupportedEncodingException uee) {
                System.err.println(uee);
            }
            if (validation.hasErrors()){
                render("Invitation/index.html");
            } 
            else
            {
                if(renderArgs.get("domainName")!=null){
                    community=renderArgs.get("domainName").toString();
                }
                if (langue.equals("fr")) {
                	Mails.inviteFr("Hypertopic Team <noreply@hypertopic.org>", mail, prenom, nom, url, community, firstNameGodfather, lastNameGodfather, mailGodfather);
                } 
                
                else
                {
                	Mails.inviteEn("Hypertopic Team <noreply@hypertopic.org>", mail, prenom, nom, url, community, firstNameGodfather, lastNameGodfather, mailGodfather);
                }
                flash.success(Messages.get("invitation_success"));
                System.out.println("community: "+community);
                
                session.remove("nom");
                session.remove("prenom");
                session.remove("mail");
                Invitation.invitation();              
            }	
            
                }
            else{
                if (langue.equals("fr")) {
                    flash.error(Messages.get("invitation_mailadresse_no_match"));
                } 
                else {
                    flash.error(Messages.get("invitation_mailadresse_no_match"));
                }


                Invitation.invitation();
            }
                }catch (Exception e) {
		System.out.println("An exception occurred in Invitation.inviteNewMember");
		e.printStackTrace();
		render("Invitation/index.html"); }


	}
         public static int verifyMaliciousPassword(String login, String mail){
                String mailAdresse = "";
                Ldap adminConnection = new Ldap();
                adminConnection.SetEnv(Play.configuration.getProperty("ldap.host"),Play.configuration.getProperty("ldap.admin.dn"), Play.configuration.getProperty("ldap.admin.password"));
                Attributes f=adminConnection.getUserInfo(adminConnection.getLdapEnv(),login);
                try{
                    NamingEnumeration e=f.getAll();
                    while(e.hasMore()){
                         javax.naming.directory.Attribute a=(javax.naming.directory.Attribute)e.next();
                         String attributeName=a.getID();
                         String attributeValue="";
                         Enumeration values = a.getAll();
                         while(values.hasMoreElements())
                         {
                                attributeValue = values.nextElement().toString();
                         }
                         if(attributeName.equals("mail"))
                         {
                                                mailAdresse = attributeValue;
                                        }
                   }
                }catch(javax.naming.NamingException e) {
                        System.out.println(e.getMessage());
                        return 0;
                }finally{
                if(mailAdresse.equals("")){
                    return Invitation.USER_NOTEXIST;
                }
                else if(mailAdresse.equals(mail))
                {
                    return Invitation.ADDRESSES_MATCHE;
                }
                else
                {
                    return Invitation.ADDRESSES_NOTMATCHE;
                }
                }

         }
	
	public static void sendInvitation(
		String firstNameSender,
		String lastNameSender,
		@Required(message="First Name Receiver is required") String firstNameReceiver,
		@Required(message="Last Name Receiver is required") String lastNameReceiver,
		@Email @Required(message="Mail Receiver is required") String mailReceiver,
		@Required(message="Message Language is required")String msgLang
	){
		String signature ="hash";
		String message="";
		String sender="yessoufy@gmail.com";
		
		if (validation.hasErrors()){
				render("Application/invitation.html");
		}else{
			if(msgLang.equals("fr")){
				message = " Vous avez 锟�锟�invit锟�e) par "+firstNameSender+" "+lastNameSender+"锟�rejoindre la communaut锟�hypertopic. Pour ce faire, veuillez vous inscrire en cliquant ici:";//Lien.signature;
			}else if(msgLang.equals("en")){
				message = "You have been invited by "+firstNameSender+" "+lastNameSender+" to register as a member to the Hypertopic community.";//Lien.signature;
			}
			//Mail.send(sender, "essoufy_@hotmail.com","sujet", message);
			flash.success(Messages.get("invitation_success"));
		}
		//show(); la vue pour afficher les erreur ou le succes de l'envoi d'invitation
	}
	
}
