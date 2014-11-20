/**
 * 
 */
package org.commcare.android.models;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.commcare.android.database.user.models.User;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.suite.model.Detail;
import org.commcare.suite.model.DetailField;
import org.commcare.suite.model.Text;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathException;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

/**
 * @author ctsims
 *
 */
public class NodeEntityFactory {

    protected EvaluationContext ec;
    
    protected Detail detail;
    protected FormInstance instance;
    protected User current; 
    
    public Detail getDetail() {
        return detail;
    }

    
    public NodeEntityFactory(Detail d, EvaluationContext ec) {
        this.detail = d;
        this.ec = ec;
    }

    public Entity<TreeReference> getEntity(TreeReference data) throws SessionUnavailableException {
        EvaluationContext nodeContext = new EvaluationContext(ec, data);
        Hashtable<String, XPathExpression> variables = getDetail().getVariableDeclarations();
        //These are actually in an ordered hashtable, so we can't just get the keyset, since it's
        //in a 1.3 hashtable equivalent
        for(Enumeration<String> en = variables.keys(); en.hasMoreElements();) {
            String key = en.nextElement();
            nodeContext.setVariable(key, XPathFuncExpr.unpack(variables.get(key).eval(nodeContext)));
        }
        
        //return new AsyncEntity<TreeReference>(detail.getFields(), nodeContext, data);
        
        int length = detail.getHeaderForms().length;
        Object[] details = new Object[length];
        String[] sortDetails = new String[length];
		String[] backgroundDetails = new String[length];
		boolean[] relevancyDetails = new boolean[length];
        int count = 0;
        for(DetailField f : this.getDetail().getFields()) {
            try {
                details[count] = f.getTemplate().evaluate(nodeContext);
                Text sortText = f.getSort();
				Text backgroundText = f.getBackground();
                if(sortText == null) {
                    sortDetails[count] = null;
                } else {
                    sortDetails[count] = sortText.evaluate(nodeContext);
				}
				if(backgroundText == null) {
					backgroundDetails[count] = "";
				} else {
					backgroundDetails[count] = backgroundText.evaluate(nodeContext);
                }
                relevancyDetails[count] = f.isRelevant(nodeContext);
            } catch(XPathException xpe) {
                xpe.printStackTrace();
                details[count] = "<invalid xpath: " + xpe.getMessage() + ">";
                backgroundDetails[count] = "";
            } catch (XPathSyntaxException e) {
                e.printStackTrace();
                details[count] = "<invalid xpath: " + e.getMessage() + ">";
                backgroundDetails[count] = "";
            }
            count++;
        }
        
		return new Entity<TreeReference>(details, sortDetails, backgroundDetails, relevancyDetails, data);
    }


    public List<TreeReference> expandReferenceList(TreeReference treeReference) {
        List<TreeReference> references = ec.expandReference(treeReference);
        return references;
    }
    
    /**
     * Optional: Allows the factory to make all of the entities that it has
     * returned "Ready" by performing any lazy evaluation needed for optimum 
     * usage. This preparation occurs asynchronously, and the returned entity
     * set should not be manipulated until it has completed.
     */
    public void prepareEntities() {
        //No implementation in normal factory
    }
    
    /**
     * Called only after a call to prepareEntities, this signals whether
     * the entities returned are ready for bulk operations.
     * 
     * @return True if entities returned from the factory are again ready
     * for use. False otherwise.
     */
    public boolean isEntitySetReady() {
       return true;
    }
}
