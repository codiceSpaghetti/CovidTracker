package it.unipi.dii.inginf.dsmt.covidtracker.web.servlets;

import it.unipi.dii.inginf.dsmt.covidtracker.intfs.HierarchyConnectionsRetriever;

import javax.ejb.EJB;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.util.List;

@WebServlet(name = "IndexServlet", urlPatterns={"/", "/index.jsp"})
public class IndexServlet extends HttpServlet {

    private static final String serverAreaPage = "/server_area/serverAreaUI.jsp";

    @EJB private HierarchyConnectionsRetriever myHierarchyConnectionsRetriever;

    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            resp.setContentType("text/html");
            PrintWriter out = resp.getWriter();

            out.println("<HTML> <HEAD> <TITLE> Covid Tracker </TITLE> </HEAD> <BODY BGCOLOR=white>");
            out.println("<CENTER> <FONT size=+2> Homepage for all Area Web Servers </FONT> </CENTER> <br> <p> ");

            // Server select
            out.println("<form method=\"POST\">");
            out.println("<label for=\"server\">Choose an area server to connect:</label>");
            out.println("<select name=\"server\" id=\"server\">");

            List<String> serverNames = myHierarchyConnectionsRetriever.getAllAreasName();

            for(String serverName: serverNames) {
                out.println("<option value=\"" + serverName + "\">" + serverName.toUpperCase() + "</option>");
            }

            out.println("</select> <br><br>");
            out.println("<input type=\"submit\" name=\"Submit_Server\">");
            out.println("</form>");

            if (req.getParameter("Submit_Server") != null) {
                String server = req.getParameter("server");
                HttpSession session = req.getSession(true);
                session.setAttribute("areaServer", server);

                RequestDispatcher disp = getServletContext().getRequestDispatcher(serverAreaPage);
                if (disp != null) disp.forward(req, resp);
            }

            out.println("</BODY> </HTML> ");

        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("webclient servlet test failed");
            throw new ServletException(ex);
        }
    }


}
