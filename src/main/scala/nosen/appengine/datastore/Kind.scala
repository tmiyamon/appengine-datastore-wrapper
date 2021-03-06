package nosen.appengine.datastore

import collection.mutable.Map
import com.google.appengine.api._
import datastore.Entity
import datastore.Key
import datastore.KeyFactory
import datastore.EntityNotFoundException
import datastore.DatastoreServiceFactory
import datastore.Query
import datastore.FetchOptions
import datastore.GeoPt
import datastore.IMHandle
import com.google.appengine.api.users.User
import com.google.appengine.api.blobstore.BlobKey
import collection.JavaConversions._
import java.util.Date

trait Kind extends Properties {
  val kindName :String = this.getClass.getSimpleName.split("\\$").last

  def datastore = DatastoreServiceFactory.getDatastoreService

  type Wrapper = EntityWrapper[this.type]
  type Member[A] = PartialFunction[Wrapper, A]

  case class Filter[A](prop:Kind.this.Property[A], value:A ,op:Query.FilterOperator) {
    def rawValue:Any = prop.toRawValue(value)
  }

  case class Sort(prop:Kind.this.Property[_], direction:Query.SortDirection = Query.SortDirection.ASCENDING)

  class Descendant[A <:Kind#Wrapper](wrapper:A) {
    def findAll:QueryWrapper = childrenOf(wrapper.key)
    def create:Wrapper = newInstanceAsChild(wrapper.key)
  }

  class DescendantOf[A <: Kind#Wrapper](kind:OtherKind[A]) extends PartialFunction[A, Descendant[A]] {
    def apply(wrapper:A) = new Descendant(wrapper)
    def isDefinedAt(wrapper:A) = true
  }

  class ParentOf[A <: Kind#Wrapper](kind:OtherKind[A]) extends PartialFunction[A, Option[Wrapper]] {
    def apply(descendant:A) = findByKey(descendant.key.getParent)
    def isDefinedAt(descendant:A) = true
  }

  case class QueryWrapper (
    ancestor:Option[Key] = None,
    filter:Seq[Filter[_]] = Seq.empty, sort:Seq[Sort] = Seq.empty,
    limitOpt:Option[Int] = None, offsetOpt:Option[Int] = None) extends Iterable[Wrapper] {

    def iterator:Iterator[Wrapper] = {
      val ds = datastore 
      ds.prepare(query).asIterator(fetchOptions).map(newInstance)
    }

    def fetchOptions = {
      import FetchOptions.Builder._
      
      val fo = withDefaults
      limitOpt.foreach(fo.limit)
      offsetOpt.foreach(fo.offset)
      fo
    } 

    def query = {
      val q = ancestor.map(new Query(kindName, _))getOrElse(new Query(kindName))
      for(f <- filter) q.addFilter(f.prop.name, f.op, f.rawValue) 
      for(s <- sort) q.addSort(s.prop.name, s.direction)
      q
    }

    def where(f:Kind.this.Filter[_]*) = copy(filter = (filter ++ f))

    def orderBy(s:Kind.this.Sort*) = copy(sort = (sort ++ s)) 

    def limit(i:Int) = copy(limitOpt = Some(i))

    def offset(i:Int) = copy(offsetOpt = Some(i))
  }

  def findById(id:Long):Option[Wrapper] = findByKey(KeyFactory.createKey(kindName, id))

  def findByName(name:String):Option[Wrapper] = findByKey(KeyFactory.createKey(kindName, name))

  def findByKey(key:Key):Option[Wrapper] = {
    if(key.getKind != kindName) 
      throw new IllegalArgumentException("A kind of key must be same as " + 
					 kindName + "but is " + key.getKind) 
    try {
      Some(newInstance(datastore.get(key)))
    } catch {
      case e:EntityNotFoundException => None
    }
  }

  val findAll:QueryWrapper = QueryWrapper()

  def childrenOf(ancestor:Key):QueryWrapper = QueryWrapper(ancestor = Some(ancestor))

  def create:Wrapper = newInstance(new Entity(kindName))

  def newInstance(keyname:String):Wrapper = 
    newInstance(new Entity(kindName, keyname))

  def newInstanceAsChild(parent:Key):Wrapper = 
    newInstance(new Entity(kindName, parent))

  def newInstance(entity:Entity):Wrapper = new Wrapper(entity, this)

}

