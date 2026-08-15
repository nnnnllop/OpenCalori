package com.opencalori.app.ui.textfood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencalori.app.domain.model.EstimatedIngredient
import com.opencalori.app.domain.model.MealType
import com.opencalori.app.domain.model.RecognizedDish
import com.opencalori.app.domain.repository.AiRepository
import com.opencalori.app.domain.repository.MealRepository
import com.opencalori.app.ui.navigation.Routes
import com.opencalori.app.ui.util.NumberFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TextFoodState(val query:String="",val mealType:MealType=MealType.SNACK,val dish:RecognizedDish?=null,val estimated:List<EstimatedIngredient> = emptyList(),val busy:Boolean=false,val saved:Boolean=false,val error:String?=null)
@HiltViewModel class TextFoodViewModel @Inject constructor(private val ai:AiRepository,private val meals:MealRepository,saved:SavedStateHandle):ViewModel(){
 private val date=saved.get<Long>(Routes.ARG_DATE)?:LocalDate.now().toEpochDay();private val _state=MutableStateFlow(TextFoodState());val state=_state.asStateFlow()
 fun setQuery(v:String)=_state.update{it.copy(query=v,error=null)}
 fun setMealType(v:MealType)=_state.update{it.copy(mealType=v)}
 fun recognize(){val q=_state.value.query.trim();if(q.isBlank())return;_state.update{it.copy(busy=true,error=null)};viewModelScope.launch{ai.recognizeText(q).onSuccess{d->_state.update{it.copy(dish=d,busy=false)}}.onFailure{e->_state.update{it.copy(busy=false,error=e.message?:"╨Э╨╡ ╤Г╨┤╨░╨╗╨╛╤Б╤М ╤А╨░╨╖╨╛╨▒╤А╨░╤В╤М ╨╛╨┐╨╕╤Б╨░╨╜╨╕╨╡")}}}}
 fun calculate(){val d=_state.value.dish?:return;val items=d.ingredients.map{it.name.trim()}.filter{it.isNotBlank()};if(items.isEmpty())return;_state.update{it.copy(busy=true,error=null)};viewModelScope.launch{ai.estimateTextNutrition(d.dishName,items).onSuccess{r->_state.update{it.copy(estimated=r,busy=false)}}.onFailure{e->_state.update{it.copy(busy=false,error=e.message?:"╨Э╨╡ ╤Г╨┤╨░╨╗╨╛╤Б╤М ╤А╨░╤Б╤Б╤З╨╕╤В╨░╤В╤М ╨Ъ╨С╨Ц╨г")}}}}
 fun grams(i:Int,v:Float)=_state.update{s->s.copy(estimated=s.estimated.toMutableList().also{it[i]=it[i].withCookedGrams(v)})}
 fun remove(i:Int)=_state.update{s->s.copy(estimated=s.estimated.filterIndexed{n,_->n!=i})}
 fun save(){val s=_state.value;val d=s.dish?:return;if(s.estimated.isEmpty()||s.estimated.any{it.effectiveGrams<=0f})return;_state.update{it.copy(busy=true)};viewModelScope.launch{meals.addItems(date,s.mealType,s.estimated.map{it.toFoodItem()},d.dishName);_state.update{it.copy(busy=false,saved=true)}}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TextFoodScreen(onBack:()->Unit,viewModel:TextFoodViewModel=hiltViewModel()){val s by viewModel.state.collectAsState();LaunchedEffect(s.saved){if(s.saved)onBack()};Scaffold(topBar={TopAppBar(title={Text("╨Ю╨┐╨╕╤Б╨░╤В╤М ╨╡╨┤╤Г")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"╨Э╨░╨╖╨░╨┤")}})}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("AI ╨╛╨┐╤А╨╡╨┤╨╡╨╗╨╕╤В ╨▒╨╗╤О╨┤╨╛ ╨╕ ╤Б╨╛╤Б╤В╨░╨▓, ╨░ ╨▓╨╡╤Б ╨║╨░╨╢╨┤╨╛╨│╨╛ ╨┐╤А╨╛╨┤╤Г╨║╤В╨░ ╨▓╤Л ╤Г╨║╨░╨╢╨╡╤В╨╡ ╤Б╨░╨╝╨╕.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);OutlinedTextField(s.query,viewModel::setQuery,label={Text("╨з╤В╨╛ ╨▓╤Л ╤Б╤К╨╡╨╗╨╕?")},placeholder={Text("╨Э╨░╨┐╤А╨╕╨╝╨╡╤А: ╨┐╨░╤Б╤В╨░ ╨║╨░╤А╨▒╨╛╨╜╨░╤А╨░ ╨╕ ╤Б╨░╨╗╨░╤В")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5);Button(viewModel::recognize,enabled=s.query.isNotBlank()&&!s.busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("╨Ю╨┐╤А╨╡╨┤╨╡╨╗╨╕╤В╤М ╨▒╨╗╤О╨┤╨╛ ╨╕ ╤Б╨╛╤Б╤В╨░╨▓")};s.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth());s.dish?.let{d->Text(d.dishName,style=MaterialTheme.typography.titleLarge);d.ingredients.forEach{Text("тАв ${it.name}",style=MaterialTheme.typography.bodyLarge)};if(s.estimated.isEmpty())Button(viewModel::calculate,enabled=!s.busy,modifier=Modifier.fillMaxWidth()){Text("╨а╨░╤Б╤Б╤З╨╕╤В╨░╤В╤М ╨Ъ╨С╨Ц╨г")}};if(s.estimated.isNotEmpty()){Text("╨г╨║╨░╨╢╨╕╤В╨╡ ╨▓╨╡╤Б ╨║╨░╨╢╨┤╨╛╨│╨╛ ╨┐╤А╨╛╨┤╤Г╨║╤В╨░",style=MaterialTheme.typography.titleMedium);LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){itemsIndexed(s.estimated){i,item->var text by remember(item.id){mutableStateOf(NumberFormat.compact(item.effectiveGrams))};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.name,style=MaterialTheme.typography.titleSmall);Text("${item.caloriesPer100g.toInt()} ╨║╨║╨░╨╗ / 100 ╨│",style=MaterialTheme.typography.bodySmall)};OutlinedTextField(text,{v->text=NumberFormat.sanitizeDecimalInput(v);NumberFormat.parse(text)?.let{viewModel.grams(i,it)}},label={Text("╨│")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.width(92.dp));IconButton({viewModel.remove(i)}){Icon(Icons.Default.Delete,"╨г╨┤╨░╨╗╨╕╤В╤М ${item.name}")}}}};Button(viewModel::save,enabled=!s.busy&&s.estimated.all{it.effectiveGrams>0f},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Check,null);Spacer(Modifier.width(8.dp));Text("╨б╨╛╤Е╤А╨░╨╜╨╕╤В╤М ╨▓ ╨┤╨╜╨╡╨▓╨╜╨╕╨║")}}}}}
