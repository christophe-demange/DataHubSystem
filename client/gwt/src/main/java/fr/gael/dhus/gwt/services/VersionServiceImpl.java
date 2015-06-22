/*
 * Data Hub Service (DHuS) - For Space data distribution.
 * Copyright (C) 2013,2014,2015 GAEL Systems
 *
 * This file is part of DHuS software sources.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.gael.dhus.gwt.services;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

import fr.gael.dhus.DHuS;
import fr.gael.dhus.gwt.services.annotation.RPCService;

/**
 * DHuS Server Version extraction
 *
 */
@RPCService ("versionService")
public class VersionServiceImpl extends RemoteServiceServlet implements
   VersionService
{
   private static final long serialVersionUID = -7473092704676835185L;

   /**
    * Retrieve the server version
    * @return
    */
   public String getVersion ()
   {
      String version = DHuS.class.getPackage ().
         getImplementationVersion ();
      return (version==null?"Development":(version));
   }

}
